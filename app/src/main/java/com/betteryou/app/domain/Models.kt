package com.betteryou.app.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** App-wide preferences. A single instance is created on first launch. */
data class AppSettings(
    val frequencyValue: Int = 2,
    val frequencyUnit: FrequencyUnit = FrequencyUnit.WEEKS,
    /** `null` uses the default reset length (1/4 of the cycle, or the whole cycle if under 7 days). */
    val customResetDays: Int? = null,
    val checkInRemindersEnabled: Boolean = true,
    val dailyCheckInEnabled: Boolean = true,
    val dailyReminderEnabled: Boolean = false,
    val dailyReminderHour: Int = 20,
    val dailyReminderMinute: Int = 0,
    val hasCompletedOnboarding: Boolean = false,
) {
    val frequency: CheckInFrequency get() = CheckInFrequency(frequencyValue, frequencyUnit)
}

/** A hobby, good habit, bad habit or break activity. */
data class TrackedItem(
    val id: String,
    val name: String,
    val kind: ItemKind,
    val createdAt: Instant,
)

/** One check-in period: a reset at the start, then a maintenance stretch, then a check-in. */
data class Cycle(
    val id: String,
    val startDate: Instant,
    val resetEndDate: Instant,
    val checkInDate: Instant,
    val focusItemIds: Set<String>,
    val completedAt: Instant? = null,
) {
    /** False when the reset covers the whole cycle (cycles shorter than a week by default). */
    val hasPostResetPeriod: Boolean get() = resetEndDate < checkInDate

    fun phase(now: Instant = Instant.now()): CyclePhase = Schedule.phase(now, resetEndDate, checkInDate)

    fun progress(now: Instant = Instant.now()): Double = fraction(now)

    val resetFraction: Double get() = fraction(resetEndDate)

    private fun fraction(point: Instant): Double {
        val total = Duration.between(startDate, checkInDate).toMillis()
        if (total <= 0) return 1.0
        return (Duration.between(startDate, point).toMillis().toDouble() / total).coerceIn(0.0, 1.0)
    }

    /** Which stretch of the cycle the habit ratings at a check-in done at [now] cover. */
    fun habitRatingPeriod(now: Instant = Instant.now()): HabitRatingPeriod = when {
        now < resetEndDate -> HabitRatingPeriod.DURING_RESET
        hasPostResetPeriod -> HabitRatingPeriod.AFTER_RESET
        else -> HabitRatingPeriod.WHOLE_CYCLE_RESET
    }

    /** Items of [kind] the user is focusing on this cycle, in list order. */
    fun focused(items: List<TrackedItem>, kind: ItemKind): List<TrackedItem> =
        items.filter { it.kind == kind && it.id in focusItemIds }
}

enum class HabitRatingPeriod {
    /** Checking in early, before the reset has ended. */
    DURING_RESET,

    /** The usual case: rate the stretch between the end of the reset and the check-in. */
    AFTER_RESET,

    /** The reset lasted the whole cycle (short cycles). */
    WHOLE_CYCLE_RESET,
}

/**
 * Ratings for one item at one check-in. Hobbies use consistency/enjoyment/improvement;
 * habits and breaks use adherence. The name and kind are snapshotted so history survives deletion.
 */
data class ItemRating(
    val itemId: String,
    val itemName: String,
    val kind: ItemKind,
    val consistency: Int? = null,
    val enjoyment: Int? = null,
    val improvement: Int? = null,
    val adherence: Int? = null,
    /** Reset days the habit was checked off on the daily checklist, out of [resetDaysTotal]. */
    val resetDaysKept: Int? = null,
    val resetDaysTotal: Int? = null,
) {
    /** Mean of the three hobby scores, if present. */
    val hobbyAverage: Double?
        get() {
            val values = listOfNotNull(consistency, enjoyment, improvement)
            if (values.isEmpty()) return null
            return values.sum().toDouble() / values.size
        }
}

/** The record of a completed check-in. */
data class CheckIn(
    val id: String,
    val date: Instant,
    val note: String,
    val cycleId: String?,
    val ratings: List<ItemRating>,
)

/** Optional daily "how do I feel today" log. One entry per day. */
data class MoodEntry(
    val day: LocalDate,
    val mood: Int,
    val note: String,
)

/** Hobby scores entered during a check-in. `null` means the user left it unrated. */
data class HobbyScores(
    val consistency: Int? = null,
    val enjoyment: Int? = null,
    val improvement: Int? = null,
) {
    val isEmpty: Boolean get() = consistency == null && enjoyment == null && improvement == null
}

/** A focused habit checked off on the daily checklist for [day]. */
data class HabitLog(val day: LocalDate, val itemId: String)

/** How many reset days so far a habit was checked off. */
data class ResetProgress(val kept: Int, val total: Int)

fun resetProgress(
    cycle: Cycle,
    itemId: String,
    logs: List<HabitLog>,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
): ResetProgress {
    val days = Schedule.resetDaysSoFar(cycle, today, zone).toSet()
    val kept = logs.count { it.itemId == itemId && it.day in days }
    return ResetProgress(kept, days.size)
}

object MoodScale {
    val emojis = listOf("😞", "🙁", "😐", "🙂", "😄")
    val labels = listOf("Awful", "Low", "Okay", "Good", "Great")

    fun emoji(value: Int): String = emojis[index(value)]
    fun label(value: Int): String = labels[index(value)]

    private fun index(value: Int): Int = value.coerceIn(1, 5) - 1
}

fun today(zone: ZoneId = ZoneId.systemDefault()): LocalDate = LocalDate.now(zone)
