package com.betteryou.app.data

import android.content.Context
import androidx.room.withTransaction
import com.betteryou.app.domain.AppSettings
import com.betteryou.app.domain.CheckIn
import com.betteryou.app.domain.Cycle
import com.betteryou.app.domain.HobbyScores
import com.betteryou.app.domain.ItemKind
import com.betteryou.app.domain.ItemRating
import com.betteryou.app.domain.MoodEntry
import com.betteryou.app.domain.Schedule
import com.betteryou.app.domain.TrackedItem
import com.betteryou.app.notifications.Reminders
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Single source of truth for app data. Keeps scheduled reminders in sync with what's stored. */
class BetterYouRepository(
    private val context: Context,
    private val db: BetterYouDatabase,
) {
    private val dao = db.dao()
    private val settingsMutex = Mutex()

    val settings: Flow<AppSettings?> = dao.observeSettings().map { it?.toDomain() }
    val items: Flow<List<TrackedItem>> = dao.observeItems().map { list -> list.map { it.toDomain() } }
    val activeCycle: Flow<Cycle?> = dao.observeActiveCycles().map { it.firstOrNull()?.toDomain() }
    val checkIns: Flow<List<CheckIn>> = dao.observeCheckIns().map { list -> list.map { it.toDomain() } }
    val moods: Flow<List<MoodEntry>> = dao.observeMoods().map { list -> list.map { it.toDomain() } }

    suspend fun ensureSettings() {
        dao.insertSettingsIfMissing(SettingsEntity.from(AppSettings()))
    }

    private suspend fun currentSettings(): AppSettings = dao.getSettings()?.toDomain() ?: AppSettings()

    /** Applies [transform] to the stored settings and reschedules anything the change affects. */
    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) = settingsMutex.withLock {
        val old = currentSettings()
        val new = transform(old)
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
        val settings = currentSettings()
        startCycle(settings, focus)
        Reminders.scheduleDailyReminder(context, settings)
        updateSettings { it.copy(hasCompletedOnboarding = true) }
    }

    suspend fun addItem(id: String, name: String, kind: ItemKind) {
        dao.insertItem(ItemEntity(id, name, kind.name, System.currentTimeMillis()))
    }

    suspend fun renameItem(id: String, name: String) = dao.renameItem(id, name)

    suspend fun deleteItem(id: String) = db.withTransaction {
        for (entity in dao.getActiveCycles()) {
            val cycle = entity.toDomain()
            if (id in cycle.focusItemIds) {
                dao.updateCycle(CycleEntity.from(cycle.copy(focusItemIds = cycle.focusItemIds - id)))
            }
        }
        dao.deleteItem(id)
    }

    suspend fun startCycle(settings: AppSettings? = null, focus: Set<String>, start: Instant = Instant.now()): Cycle {
        val current = settings ?: currentSettings()
        val validIds = dao.getItems().map { it.id }.toSet()
        val dates = Schedule.cycleDates(start, current.frequency, current.customResetDays)
        val cycle = Cycle(
            id = UUID.randomUUID().toString(),
            startDate = start,
            resetEndDate = dates.resetEnd,
            checkInDate = dates.checkIn,
            focusItemIds = focus intersect validIds,
        )
        dao.insertCycle(CycleEntity.from(cycle))
        Reminders.scheduleCycleReminders(context, cycle, current.checkInRemindersEnabled)
        return cycle
    }

    suspend fun updateFocus(cycleId: String, focus: Set<String>) {
        val entity = dao.getActiveCycles().firstOrNull { it.id == cycleId } ?: return
        dao.updateCycle(CycleEntity.from(entity.toDomain().copy(focusItemIds = focus)))
    }

    /** Records the check-in, completes [cycle] and starts the next one with [nextFocus]. */
    suspend fun completeCheckIn(
        cycle: Cycle,
        hobbyScores: Map<String, HobbyScores>,
        adherence: Map<String, Int>,
        note: String,
        nextFocus: Set<String>,
    ) {
        val items = dao.getItems().map { it.toDomain() }
        val now = Instant.now()
        val checkInId = UUID.randomUUID().toString()

        val ratings = buildList {
            for (item in cycle.focused(items, ItemKind.HOBBY)) {
                val scores = hobbyScores[item.id] ?: HobbyScores()
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
                    add(ItemRating(item.id, item.name, item.kind, adherence = adherence[item.id] ?: 3))
                }
            }
        }

        db.withTransaction {
            dao.insertCheckIn(CheckInEntity(checkInId, now.toEpochMilli(), note.trim(), cycle.id))
            dao.insertRatings(ratings.map { ItemRatingEntity.from(checkInId, it) })
            dao.updateCycle(CycleEntity.from(cycle.copy(completedAt = now)))
        }
        startCycle(focus = nextFocus, start = now)
    }

    suspend fun saveMood(day: LocalDate, mood: Int, note: String) {
        dao.upsertMood(MoodEntity(day.toEpochDay(), mood, note.trim()))
    }

    /** Re-registers all alarms, e.g. after a reboot (alarms don't survive one). */
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
