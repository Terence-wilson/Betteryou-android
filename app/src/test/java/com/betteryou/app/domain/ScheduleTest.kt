package com.betteryou.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class ScheduleTest {
    private val utc: ZoneId = ZoneOffset.UTC
    private val start: ZonedDateTime = ZonedDateTime.of(2026, 1, 1, 9, 0, 0, 0, utc)

    @Test
    fun resetLastsWholeCycleWhenShorterThanAWeek() {
        assertEquals(1, Schedule.defaultResetDays(1))
        assertEquals(3, Schedule.defaultResetDays(3))
        assertEquals(6, Schedule.defaultResetDays(6))
    }

    @Test
    fun resetIsQuarterOfCycleForAWeekOrLonger() {
        assertEquals(2, Schedule.defaultResetDays(7))
        assertEquals(4, Schedule.defaultResetDays(14))
        assertEquals(7, Schedule.defaultResetDays(28))
        assertEquals(8, Schedule.defaultResetDays(31))
    }

    @Test
    fun customResetIsClampedToCycle() {
        assertEquals(10, Schedule.resetDays(cycleDays = 10, override = 20))
        assertEquals(1, Schedule.resetDays(cycleDays = 10, override = 0))
        assertEquals(5, Schedule.resetDays(cycleDays = 10, override = 5))
        assertEquals(3, Schedule.resetDays(cycleDays = 10, override = null))
    }

    @Test
    fun longerThanAMonthWarning() {
        assertFalse(CheckInFrequency(31, FrequencyUnit.DAYS).isLongerThanAMonth)
        assertTrue(CheckInFrequency(32, FrequencyUnit.DAYS).isLongerThanAMonth)
        assertFalse(CheckInFrequency(4, FrequencyUnit.WEEKS).isLongerThanAMonth)
        assertTrue(CheckInFrequency(5, FrequencyUnit.WEEKS).isLongerThanAMonth)
        assertFalse(CheckInFrequency(1, FrequencyUnit.MONTHS).isLongerThanAMonth)
        assertTrue(CheckInFrequency(2, FrequencyUnit.MONTHS).isLongerThanAMonth)
    }

    @Test
    fun frequencyLength() {
        assertEquals(14, CheckInFrequency(2, FrequencyUnit.WEEKS).lengthInDays(start))
        assertEquals(5, CheckInFrequency(5, FrequencyUnit.DAYS).lengthInDays(start))
        assertEquals(31, CheckInFrequency(1, FrequencyUnit.MONTHS).lengthInDays(start))
    }

    @Test
    fun phases() {
        val resetEnd = start.plusDays(4).toInstant()
        val checkIn = start.plusDays(14).toInstant()

        assertEquals(CyclePhase.Reset(4), Schedule.phase(start.toInstant(), resetEnd, checkIn, utc))
        assertEquals(CyclePhase.Maintain(9), Schedule.phase(start.plusDays(5).toInstant(), resetEnd, checkIn, utc))
        assertEquals(CyclePhase.CheckInDue, Schedule.phase(checkIn, resetEnd, checkIn, utc))
    }

    @Test
    fun fullResetCycleGoesStraightToCheckIn() {
        val checkIn = start.plusDays(5).toInstant()
        val day4 = start.plusDays(4).toInstant()
        assertEquals(CyclePhase.Reset(1), Schedule.phase(day4, checkIn, checkIn, utc))
        assertEquals(CyclePhase.CheckInDue, Schedule.phase(checkIn, checkIn, checkIn, utc))
    }

    private val midnight: ZonedDateTime = start.toLocalDate().atStartOfDay(utc)

    @Test
    fun cycleDatesUseQuarterResetForTwoWeeks() {
        val dates = Schedule.cycleDates(start.toInstant(), CheckInFrequency(2, FrequencyUnit.WEEKS), null, utc)
        assertEquals(midnight.toInstant(), dates.start)
        assertEquals(midnight.plusDays(14).toInstant(), dates.checkIn)
        assertEquals(midnight.plusDays(4).toInstant(), dates.resetEnd)
    }

    @Test
    fun cyclesStartAtMidnightWhateverTheTimeOfDay() {
        val lateNight = start.withHour(23).withMinute(40).toInstant()
        val early = start.withHour(0).withMinute(5).toInstant()
        val frequency = CheckInFrequency(1, FrequencyUnit.WEEKS)
        assertEquals(
            Schedule.cycleDates(early, frequency, null, utc),
            Schedule.cycleDates(lateNight, frequency, null, utc),
        )
    }

    @Test
    fun daysRemainingCountsCalendarDays() {
        val tomorrow = midnight.plusDays(1).toInstant()
        assertEquals(1, Schedule.daysRemaining(start.withHour(23).toInstant(), tomorrow, utc))
        assertEquals(1, Schedule.daysRemaining(start.withHour(1).toInstant(), tomorrow, utc))
        assertEquals(3, Schedule.daysRemaining(start.toInstant(), midnight.plusDays(3).toInstant(), utc))
    }

    @Test
    fun remindersGoOutInTheMorningOfTheDueDay() {
        val due = midnight.plusDays(14).toInstant()
        assertEquals(midnight.plusDays(14).withHour(Schedule.REMINDER_HOUR).toInstant(), Schedule.reminderTime(due, utc))
        // A boundary later in the day (older cycles) is never reminded about early.
        val evening = midnight.plusDays(14).withHour(21).toInstant()
        assertEquals(evening, Schedule.reminderTime(evening, utc))
    }

    @Test
    fun unitsHaveSensibleMaximums() {
        assertEquals(365, FrequencyUnit.DAYS.maxValue)
        assertEquals(52, FrequencyUnit.WEEKS.maxValue)
        assertEquals(12, FrequencyUnit.MONTHS.maxValue)
    }

    private fun cycle(resetDays: Long, totalDays: Long) = Cycle(
        id = "c",
        startDate = midnight.toInstant(),
        resetEndDate = midnight.plusDays(resetDays).toInstant(),
        checkInDate = midnight.plusDays(totalDays).toInstant(),
        focusItemIds = emptySet(),
    )

    @Test
    fun habitRatingPeriodDependsOnWhenYouCheckIn() {
        val twoWeeks = cycle(4, 14)
        assertEquals(HabitRatingPeriod.DURING_RESET, twoWeeks.habitRatingPeriod(midnight.plusDays(2).toInstant()))
        assertEquals(HabitRatingPeriod.AFTER_RESET, twoWeeks.habitRatingPeriod(midnight.plusDays(10).toInstant()))
        val fiveDays = cycle(5, 5)
        assertEquals(HabitRatingPeriod.DURING_RESET, fiveDays.habitRatingPeriod(midnight.plusDays(2).toInstant()))
        assertEquals(HabitRatingPeriod.WHOLE_CYCLE_RESET, fiveDays.habitRatingPeriod(midnight.plusDays(5).toInstant()))
    }

    @Test
    fun resetDaysSoFarStopsAtTodayAndAtTheResetEnd() {
        val twoWeeks = cycle(4, 14)
        val day0 = midnight.toLocalDate()
        assertEquals(listOf(day0, day0.plusDays(1)), Schedule.resetDaysSoFar(twoWeeks, day0.plusDays(1), utc))
        assertEquals(4, Schedule.resetDaysSoFar(twoWeeks, day0.plusDays(10), utc).size)
        assertEquals(emptyList<java.time.LocalDate>(), Schedule.resetDaysSoFar(twoWeeks, day0.minusDays(1), utc))
    }

    @Test
    fun resetProgressCountsOnlyLogsInsideTheReset() {
        val twoWeeks = cycle(4, 14)
        val day0 = midnight.toLocalDate()
        val logs = listOf(
            HabitLog(day0, "water"),
            HabitLog(day0.plusDays(2), "water"),
            HabitLog(day0.plusDays(6), "water"),
            HabitLog(day0, "other"),
        )
        assertEquals(ResetProgress(2, 4), resetProgress(twoWeeks, "water", logs, day0.plusDays(10), utc))
    }

    @Test
    fun cycleDatesUseWholeCycleForShortCycles() {
        val dates = Schedule.cycleDates(start.toInstant(), CheckInFrequency(5, FrequencyUnit.DAYS), null, utc)
        assertEquals(dates.checkIn, dates.resetEnd)
    }

    @Test
    fun cycleDatesHonourCustomReset() {
        val dates = Schedule.cycleDates(start.toInstant(), CheckInFrequency(1, FrequencyUnit.MONTHS), 10, utc)
        assertEquals(midnight.plusMonths(1).toInstant(), dates.checkIn)
        assertEquals(midnight.plusDays(10).toInstant(), dates.resetEnd)
    }

    @Test
    fun nextDailyOccurrence() {
        val eightPm = LocalTime.of(20, 0)
        assertEquals(start.withHour(20), Schedule.nextDailyOccurrence(start, eightPm))
        val ninePm = start.withHour(21)
        assertEquals(start.plusDays(1).withHour(20), Schedule.nextDailyOccurrence(ninePm, eightPm))
        assertEquals(start.plusDays(1).withHour(20), Schedule.nextDailyOccurrence(start.withHour(20), eightPm))
    }

    @Test
    fun cycleProgressAndResetFraction() {
        val cycle = Cycle(
            id = "c",
            startDate = start.toInstant(),
            resetEndDate = start.plusDays(4).toInstant(),
            checkInDate = start.plusDays(16).toInstant(),
            focusItemIds = emptySet(),
        )
        assertEquals(0.25, cycle.resetFraction, 1e-9)
        assertEquals(0.5, cycle.progress(start.plusDays(8).toInstant()), 1e-9)
        assertEquals(1.0, cycle.progress(start.plusDays(30).toInstant()), 1e-9)
        assertTrue(cycle.hasPostResetPeriod)
    }
}
