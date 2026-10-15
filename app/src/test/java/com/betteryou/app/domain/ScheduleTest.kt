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

        assertEquals(CyclePhase.Reset(4), Schedule.phase(start.toInstant(), resetEnd, checkIn))
        assertEquals(CyclePhase.Maintain(9), Schedule.phase(start.plusDays(5).toInstant(), resetEnd, checkIn))
        assertEquals(CyclePhase.CheckInDue, Schedule.phase(checkIn, resetEnd, checkIn))
    }

    @Test
    fun fullResetCycleGoesStraightToCheckIn() {
        val checkIn = start.plusDays(5).toInstant()
        val day4 = start.plusDays(4).toInstant()
        assertEquals(CyclePhase.Reset(1), Schedule.phase(day4, checkIn, checkIn))
        assertEquals(CyclePhase.CheckInDue, Schedule.phase(checkIn, checkIn, checkIn))
    }

    @Test
    fun cycleDatesUseQuarterResetForTwoWeeks() {
        val dates = Schedule.cycleDates(start.toInstant(), CheckInFrequency(2, FrequencyUnit.WEEKS), null, utc)
        assertEquals(start.plusDays(14).toInstant(), dates.checkIn)
        assertEquals(start.plusDays(4).toInstant(), dates.resetEnd)
    }

    @Test
    fun cycleDatesUseWholeCycleForShortCycles() {
        val dates = Schedule.cycleDates(start.toInstant(), CheckInFrequency(5, FrequencyUnit.DAYS), null, utc)
        assertEquals(dates.checkIn, dates.resetEnd)
    }

    @Test
    fun cycleDatesHonourCustomReset() {
        val dates = Schedule.cycleDates(start.toInstant(), CheckInFrequency(1, FrequencyUnit.MONTHS), 10, utc)
        assertEquals(start.plusMonths(1).toInstant(), dates.checkIn)
        assertEquals(start.plusDays(10).toInstant(), dates.resetEnd)
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
