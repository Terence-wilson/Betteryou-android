package com.betteryou.app.data

import android.content.Context
import androidx.room.withTransaction
import com.betteryou.app.domain.AppSettings
import com.betteryou.app.domain.CheckIn
import com.betteryou.app.domain.Cycle
import com.betteryou.app.domain.HabitLog
import com.betteryou.app.domain.HobbyScores
import com.betteryou.app.domain.ItemKind
import com.betteryou.app.domain.ItemRating
import com.betteryou.app.domain.MoodEntry
import com.betteryou.app.domain.Schedule
import com.betteryou.app.domain.TrackedItem
import com.betteryou.app.domain.resetProgress
import com.betteryou.app.domain.today
import com.betteryou.app.notifications.Reminders
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** What's needed to undo deleting an item. */
data class DeletedItem(val item: TrackedItem, val focusedInCycleIds: Set<String>)

/** Single source of truth for app data. Keeps scheduled reminders in sync with what's stored. */
class BetterYouRepository(
    private val context: Context,
    private val db: BetterYouDatabase,
) {
    private val dao = db.dao()
    private val settingsMutex = Mutex()

    /** Serialises cycle changes so double taps can't start two cycles or complete one twice. */
    private val cycleMutex = Mutex()

    val settings: Flow<AppSettings?> = dao.observeSettings().map { it?.toDomain() }
    val items: Flow<List<TrackedItem>> = dao.observeItems().map { list -> list.map { it.toDomain() } }
    val activeCycle: Flow<Cycle?> = dao.observeActiveCycles().map { it.firstOrNull()?.toDomain() }
    val checkIns: Flow<List<CheckIn>> = dao.observeCheckIns().map { list -> list.map { it.toDomain() } }
    val moods: Flow<List<MoodEntry>> = dao.observeMoods().map { list -> list.map { it.toDomain() } }
    val habitLogs: Flow<List<HabitLog>> = dao.observeHabitLogs().map { list -> list.map { it.toDomain() } }

    suspend fun ensureSettings() {
        dao.insertSettingsIfMissing(SettingsEntity.from(AppSettings()))
    }

    private suspend fun currentSettings(): AppSettings = dao.getSettings()?.toDomain() ?: AppSettings()

    /** Applies [transform] to the stored settings and reschedules anything the change affects. */
    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) = settingsMutex.withLock {
        val old = currentSettings()
        val transformed = transform(old)
        val new = transformed.copy(
            frequencyValue = transformed.frequencyValue.coerceIn(1, transformed.frequencyUnit.maxValue),
        )
        if (new == old) return@withLock
        dao.upsertSettings(SettingsEntity.from(new))

        val scheduleChanged = old.frequency != new.frequency || old.customResetDays != new.customResetDays
        if (scheduleChanged || old.checkInRemindersEnabled != new.checkInRemindersEnabled) {
            for (entity in dao.getActiveCycles()) {
                var cycle = entity.toDomain()
                if (scheduleChanged) {
                    // Changes to frequency and reset length apply to the running cycle immediately.
                    val dates = Schedule.cycleDates(cycle.startDate, new.frequency, new.customResetDays)
                    cycle = cycle.copy(resetEndDate = dates.resetEnd, checkInDate = dates.checkIn)
                    dao.updateCycle(CycleEntity.from(cycle))
                }
                Reminders.scheduleCycleReminders(context, cycle, new.checkInRemindersEnabled)
            }
        }

        val dailyChanged = old.dailyCheckInEnabled != new.dailyCheckInEnabled ||
            old.dailyReminderEnabled != new.dailyReminderEnabled ||
            old.dailyReminderHour != new.dailyReminderHour ||
            old.dailyReminderMinute != new.dailyReminderMinute
        if (dailyChanged) Reminders.scheduleDailyReminder(context, new)
    }

    suspend fun finishOnboarding(focus: Set<String>) {
        cycleMutex.withLock<Unit> {
            if (dao.getActiveCycles().isEmpty()) startCycleLocked(currentSettings(), focus, Instant.now())
        }
        updateSettings { it.copy(hasCompletedOnboarding = true) }
        Reminders.scheduleDailyReminder(context, currentSettings())
    }

    suspend fun addItem(id: String, name: String, kind: ItemKind) {
        dao.insertItem(ItemEntity(id, name, kind.name, System.currentTimeMillis()))
    }

    suspend fun renameItem(id: String, name: String) = dao.renameItem(id, name)

    /** Deletes an item and removes it from any running cycle's focus. Returns what's needed to undo. */
    suspend fun deleteItem(item: TrackedItem): DeletedItem = db.withTransaction {
        val focusedIn = mutableSetOf<String>()
        for (entity in dao.getActiveCycles()) {
            val cycle = entity.toDomain()
            if (item.id in cycle.focusItemIds) {
                focusedIn += cycle.id
                dao.updateCycle(CycleEntity.from(cycle.copy(focusItemIds = cycle.focusItemIds - item.id)))
            }
        }
        dao.deleteItem(item.id)
        DeletedItem(item, focusedIn)
    }

    suspend fun restoreItem(deleted: DeletedItem) = db.withTransaction {
        val item = deleted.item
        dao.insertItem(ItemEntity(item.id, item.name, item.kind.name, item.createdAt.toEpochMilli()))
        for (cycleId in deleted.focusedInCycleIds) {
            val cycle = dao.getCycle(cycleId)?.toDomain() ?: continue
            if (cycle.completedAt == null) {
                dao.updateCycle(CycleEntity.from(cycle.copy(focusItemIds = cycle.focusItemIds + item.id)))
            }
        }
    }

    /** Starts a cycle unless one is already running (e.g. from a double tap). */
    suspend fun startCycle(focus: Set<String>): Unit = cycleMutex.withLock<Unit> {
        if (dao.getActiveCycles().isEmpty()) startCycleLocked(currentSettings(), focus, Instant.now())
    }

    private suspend fun startCycleLocked(settings: AppSettings, focus: Set<String>, start: Instant): Cycle {
        val validIds = dao.getItems().map { it.id }.toSet()
        val dates = Schedule.cycleDates(start, settings.frequency, settings.customResetDays)
        val cycle = Cycle(
            id = UUID.randomUUID().toString(),
            startDate = dates.start,
            resetEndDate = dates.resetEnd,
            checkInDate = dates.checkIn,
            focusItemIds = focus intersect validIds,
        )
        dao.insertCycle(CycleEntity.from(cycle))
        Reminders.scheduleCycleReminders(context, cycle, settings.checkInRemindersEnabled)
        return cycle
    }

    suspend fun updateFocus(cycleId: String, focus: Set<String>) {
        val entity = dao.getActiveCycles().firstOrNull { it.id == cycleId } ?: return
        dao.updateCycle(CycleEntity.from(entity.toDomain().copy(focusItemIds = focus)))
    }

    suspend fun setHabitKept(itemId: String, day: LocalDate, kept: Boolean) {
        if (kept) {
            dao.insertHabitLog(HabitLogEntity(day.toEpochDay(), itemId))
        } else {
            dao.deleteHabitLog(day.toEpochDay(), itemId)
        }
    }

    /**
     * Records the check-in, completes [cycleId] and starts the next one with [nextFocus].
     * Unrated scores are left out rather than stored as a default.
     */
    suspend fun completeCheckIn(
        cycleId: String,
        hobbyScores: Map<String, HobbyScores>,
        adherence: Map<String, Int?>,
        note: String,
        nextFocus: Set<String>,
    ): Unit = cycleMutex.withLock<Unit> {
        val cycle = dao.getCycle(cycleId)?.toDomain() ?: return@withLock
        if (cycle.completedAt != null) return@withLock

        val items = dao.getItems().map { it.toDomain() }
        val now = Instant.now()
        val checkInId = UUID.randomUUID().toString()
        val day = today()

        val ratings = buildList {
            for (item in cycle.focused(items, ItemKind.HOBBY)) {
                val scores = hobbyScores[item.id] ?: HobbyScores()
                if (scores.isEmpty) continue
                add(
                    ItemRating(
                        itemId = item.id,
                        itemName = item.name,
                        kind = item.kind,
                        consistency = scores.consistency,
                        enjoyment = scores.enjoyment,
                        improvement = scores.improvement,
                    ),
                )
            }
            for (kind in ItemKind.habitKinds) {
                for (item in cycle.focused(items, kind)) {
                    val logs = dao.getHabitLogs(item.id).map { it.toDomain() }
                    val progress = resetProgress(cycle, item.id, logs, day)
                    val score = adherence[item.id]
                    if (score == null && progress.total == 0) continue
                    add(
                        ItemRating(
                            itemId = item.id,
                            itemName = item.name,
                            kind = item.kind,
                            adherence = score,
                            resetDaysKept = progress.kept.takeIf { progress.total > 0 },
                            resetDaysTotal = progress.total.takeIf { it > 0 },
                        ),
                    )
                }
            }
        }

        db.withTransaction {
            dao.insertCheckIn(CheckInEntity(checkInId, now.toEpochMilli(), note.trim(), cycle.id))
            dao.insertRatings(ratings.map { ItemRatingEntity.from(checkInId, it) })
            dao.updateCycle(CycleEntity.from(cycle.copy(completedAt = now)))
        }
        Reminders.clearCycleNotifications(context)
        startCycleLocked(currentSettings(), nextFocus, now)
    }

    suspend fun updateCheckInNote(id: String, note: String) = dao.updateCheckInNote(id, note.trim())

    suspend fun deleteCheckIn(id: String) = db.withTransaction {
        dao.deleteRatings(id)
        dao.deleteCheckIn(id)
    }

    suspend fun saveMood(day: LocalDate, mood: Int, note: String) {
        dao.upsertMood(MoodEntity(day.toEpochDay(), mood, note.trim()))
    }

    suspend fun deleteMood(day: LocalDate) = dao.deleteMood(day.toEpochDay())

    suspend fun hasMood(day: LocalDate): Boolean = dao.moodCount(day.toEpochDay()) > 0

    /** Re-registers all alarms, e.g. after a reboot or a time zone change (alarms don't survive either). */
    suspend fun rescheduleAll() {
        val settings = currentSettings()
        if (!settings.hasCompletedOnboarding) return
        Reminders.scheduleCycleReminders(
            context,
            dao.getActiveCycles().firstOrNull()?.toDomain(),
            settings.checkInRemindersEnabled,
        )
        Reminders.scheduleDailyReminder(context, settings)
    }
}
