package com.betteryou.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The unit used to express how often check-ins happen. */
enum class FrequencyUnit(val title: String) {
    DAYS("Days"),
    WEEKS("Weeks"),
    MONTHS("Months");

    /** Largest sensible value for this unit (a year). */
    val maxValue: Int
        get() = when (this) {
            DAYS -> 365
            WEEKS -> 52
            MONTHS -> 12
        }

    fun label(value: Int): String = when (this) {
        DAYS -> if (value == 1) "day" else "days"
        WEEKS -> if (value == 1) "week" else "weeks"
        MONTHS -> if (value == 1) "month" else "months"
    }

    companion object {
        fun fromStorage(value: String): FrequencyUnit = entries.firstOrNull { it.name == value } ?: WEEKS
    }
}

/** How often the user checks in. Any time frame is accepted. */
data class CheckInFrequency(val value: Int, val unit: FrequencyUnit) {
    val displayText: String get() = "$value ${unit.label(value)}"

    /** Check-ins spaced more than a month apart are not very effective, so the UI warns about them. */
    val isLongerThanAMonth: Boolean
        get() = when (unit) {
            FrequencyUnit.DAYS -> value > 31
            FrequencyUnit.WEEKS -> value > 4
            FrequencyUnit.MONTHS -> value > 1
        }

    fun endDate(start: ZonedDateTime): ZonedDateTime {
        val amount = max(1, value).toLong()
        return when (unit) {
            FrequencyUnit.DAYS -> start.plusDays(amount)
            FrequencyUnit.WEEKS -> start.plusWeeks(amount)
            FrequencyUnit.MONTHS -> start.plusMonths(amount)
        }
    }

    fun lengthInDays(start: ZonedDateTime = ZonedDateTime.now()): Int = Schedule.days(start, endDate(start))
}

/** Where the user currently is inside a check-in cycle. */
sealed interface CyclePhase {
    /** The strict period at the start of a cycle: no bad habits, good habits every day. */
    data class Reset(val daysLeft: Int) : CyclePhase

    /** After the reset and before the check-in. */
    data class Maintain(val daysLeft: Int) : CyclePhase

    data object CheckInDue : CyclePhase

    val isReset: Boolean get() = this is Reset
}

data class CycleDates(val start: Instant, val resetEnd: Instant, val checkIn: Instant)

object Schedule {
    /** Check-in and reset reminders go out at this local hour on the day they're due. */
    const val REMINDER_HOUR = 9

    /**
     * Default reset length: 1/4 of the cycle, unless the cycle is shorter than 7 days,
     * in which case the reset lasts the whole cycle.
     */
    fun defaultResetDays(cycleDays: Int): Int {
        val days = max(1, cycleDays)
        if (days < 7) return days
        return max(1, (days / 4.0).roundToInt())
    }

    /** Reset length honouring a user override, clamped to the cycle length. */
    fun resetDays(cycleDays: Int, override: Int?): Int {
        val cycle = max(1, cycleDays)
        if (override == null) return defaultResetDays(cycle)
        return min(max(1, override), cycle)
    }

    fun days(start: ZonedDateTime, end: ZonedDateTime): Int = ChronoUnit.DAYS.between(start, end).toInt()

    fun startOfDay(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): Instant =
        instant.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()

    /** Calendar days from today until the day [end] falls on (at least 1). */
    fun daysRemaining(now: Instant, end: Instant, zone: ZoneId = ZoneId.systemDefault()): Int {
        val today = now.atZone(zone).toLocalDate()
        val endDay = end.atZone(zone).toLocalDate()
        return max(1, ChronoUnit.DAYS.between(today, endDay).toInt())
    }

    fun phase(
        now: Instant,
        resetEnd: Instant,
        checkIn: Instant,
        zone: ZoneId = ZoneId.systemDefault(),
    ): CyclePhase = when {
        now >= checkIn -> CyclePhase.CheckInDue
        now < resetEnd -> CyclePhase.Reset(daysRemaining(now, resetEnd, zone))
        else -> CyclePhase.Maintain(daysRemaining(now, checkIn, zone))
    }

    /**
     * Dates for a cycle that begins on the day of [start]. Cycles run from midnight to midnight so
     * the schedule doesn't depend on the time of day the previous check-in happened.
     */
    fun cycleDates(
        start: Instant,
        frequency: CheckInFrequency,
        customResetDays: Int?,
        zone: ZoneId = ZoneId.systemDefault(),
    ): CycleDates {
        val startDate = start.atZone(zone).toLocalDate().atStartOfDay(zone)
        val checkIn = frequency.endDate(startDate)
        val cycleDays = days(startDate, checkIn)
        val resetDays = resetDays(cycleDays, customResetDays)
        val resetEnd = startDate.plusDays(resetDays.toLong())
        return CycleDates(
            start = startDate.toInstant(),
            resetEnd = minOf(resetEnd, checkIn).toInstant(),
            checkIn = checkIn.toInstant(),
        )
    }

    /** When to remind the user about a boundary: [REMINDER_HOUR] on its day, but never before it. */
    fun reminderTime(boundary: Instant, zone: ZoneId = ZoneId.systemDefault()): Instant {
        val atHour = boundary.atZone(zone).toLocalDate().atTime(REMINDER_HOUR, 0).atZone(zone).toInstant()
        return maxOf(atHour, boundary)
    }

    /** The days of a cycle's reset that have started by [today], oldest first. */
    fun resetDaysSoFar(cycle: Cycle, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<LocalDate> {
        val first = cycle.startDate.atZone(zone).toLocalDate()
        val resetEnd = cycle.resetEndDate.atZone(zone)
        // The reset covers every day it touches before it ends.
        val lastExclusive = if (resetEnd.toLocalTime() == LocalTime.MIDNIGHT) {
            resetEnd.toLocalDate()
        } else {
            resetEnd.toLocalDate().plusDays(1)
        }
        val last = minOf(lastExclusive.minusDays(1), today)
        if (last.isBefore(first)) return emptyList()
        return generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.toList()
    }

    /** The next time the daily reminder should fire: today at [time] if still ahead, otherwise tomorrow. */
    fun nextDailyOccurrence(now: ZonedDateTime, time: LocalTime): ZonedDateTime {
        val today = now.toLocalDate().atTime(time).atZone(now.zone)
        return if (today.isAfter(now)) today else today.plusDays(1)
    }
}
