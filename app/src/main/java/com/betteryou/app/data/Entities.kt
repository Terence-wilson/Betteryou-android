package com.betteryou.app.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.betteryou.app.domain.AppSettings
import com.betteryou.app.domain.CheckIn
import com.betteryou.app.domain.Cycle
import com.betteryou.app.domain.FrequencyUnit
import com.betteryou.app.domain.ItemKind
import com.betteryou.app.domain.ItemRating
import com.betteryou.app.domain.MoodEntry
import com.betteryou.app.domain.TrackedItem
import java.time.Instant
import java.time.LocalDate

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val frequencyValue: Int,
    val frequencyUnit: String,
    val customResetDays: Int?,
    val checkInRemindersEnabled: Boolean,
    val dailyCheckInEnabled: Boolean,
    val dailyReminderEnabled: Boolean,
    val dailyReminderHour: Int,
    val dailyReminderMinute: Int,
    val hasCompletedOnboarding: Boolean,
) {
    fun toDomain() = AppSettings(
        frequencyValue = frequencyValue,
        frequencyUnit = FrequencyUnit.fromStorage(frequencyUnit),
        customResetDays = customResetDays,
        checkInRemindersEnabled = checkInRemindersEnabled,
        dailyCheckInEnabled = dailyCheckInEnabled,
        dailyReminderEnabled = dailyReminderEnabled,
        dailyReminderHour = dailyReminderHour,
        dailyReminderMinute = dailyReminderMinute,
        hasCompletedOnboarding = hasCompletedOnboarding,
    )

    companion object {
        const val SINGLETON_ID = 1

        fun from(settings: AppSettings) = SettingsEntity(
            frequencyValue = settings.frequencyValue,
            frequencyUnit = settings.frequencyUnit.name,
            customResetDays = settings.customResetDays,
            checkInRemindersEnabled = settings.checkInRemindersEnabled,
            dailyCheckInEnabled = settings.dailyCheckInEnabled,
            dailyReminderEnabled = settings.dailyReminderEnabled,
            dailyReminderHour = settings.dailyReminderHour,
            dailyReminderMinute = settings.dailyReminderMinute,
            hasCompletedOnboarding = settings.hasCompletedOnboarding,
        )
    }
}

@Entity(tableName = "items")
data class ItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val kind: String,
    val createdAt: Long,
) {
    fun toDomain() = TrackedItem(id, name, ItemKind.fromStorage(kind), Instant.ofEpochMilli(createdAt))
}

@Entity(tableName = "cycles")
data class CycleEntity(
    @PrimaryKey val id: String,
    val startDate: Long,
    val resetEndDate: Long,
    val checkInDate: Long,
    /** Comma-separated item IDs. */
    val focusItemIds: String,
    val completedAt: Long?,
) {
    fun toDomain() = Cycle(
        id = id,
        startDate = Instant.ofEpochMilli(startDate),
        resetEndDate = Instant.ofEpochMilli(resetEndDate),
        checkInDate = Instant.ofEpochMilli(checkInDate),
        focusItemIds = focusItemIds.split(',').filter { it.isNotBlank() }.toSet(),
        completedAt = completedAt?.let(Instant::ofEpochMilli),
    )

    companion object {
        fun from(cycle: Cycle) = CycleEntity(
            id = cycle.id,
            startDate = cycle.startDate.toEpochMilli(),
            resetEndDate = cycle.resetEndDate.toEpochMilli(),
            checkInDate = cycle.checkInDate.toEpochMilli(),
            focusItemIds = cycle.focusItemIds.joinToString(","),
            completedAt = cycle.completedAt?.toEpochMilli(),
        )
    }
}

@Entity(
    tableName = "check_ins",
    foreignKeys = [
        ForeignKey(
            entity = CycleEntity::class,
            parentColumns = ["id"],
            childColumns = ["cycleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("cycleId")],
)
data class CheckInEntity(
    @PrimaryKey val id: String,
    val date: Long,
    val note: String,
    val cycleId: String?,
)

@Entity(
    tableName = "item_ratings",
    foreignKeys = [
        ForeignKey(
            entity = CheckInEntity::class,
            parentColumns = ["id"],
            childColumns = ["checkInId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("checkInId")],
)
data class ItemRatingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val checkInId: String,
    val itemId: String,
    val itemName: String,
    val kind: String,
    val consistency: Int?,
    val enjoyment: Int?,
    val improvement: Int?,
    val adherence: Int?,
) {
    fun toDomain() = ItemRating(
        itemId = itemId,
        itemName = itemName,
        kind = ItemKind.fromStorage(kind),
        consistency = consistency,
        enjoyment = enjoyment,
        improvement = improvement,
        adherence = adherence,
    )

    companion object {
        fun from(checkInId: String, rating: ItemRating) = ItemRatingEntity(
            checkInId = checkInId,
            itemId = rating.itemId,
            itemName = rating.itemName,
            kind = rating.kind.name,
            consistency = rating.consistency,
            enjoyment = rating.enjoyment,
            improvement = rating.improvement,
            adherence = rating.adherence,
        )
    }
}

data class CheckInWithRatings(
    @Embedded val checkIn: CheckInEntity,
    @Relation(parentColumn = "id", entityColumn = "checkInId")
    val ratings: List<ItemRatingEntity>,
) {
    fun toDomain() = CheckIn(
        id = checkIn.id,
        date = Instant.ofEpochMilli(checkIn.date),
        note = checkIn.note,
        cycleId = checkIn.cycleId,
        ratings = ratings.sortedBy { it.id }.map { it.toDomain() },
    )
}

@Entity(tableName = "moods")
data class MoodEntity(
    /** The day as [LocalDate.toEpochDay]; one entry per day. */
    @PrimaryKey val epochDay: Long,
    val mood: Int,
    val note: String,
) {
    fun toDomain() = MoodEntry(LocalDate.ofEpochDay(epochDay), mood, note)
}
