package com.betteryou.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class MetricsTest {
    private val today = LocalDate.of(2026, 3, 10)

    private fun mood(daysAgo: Long, value: Int) = MoodEntry(today.minusDays(daysAgo), value, "")

    @Test
    fun streakCountsBackFromToday() {
        val moods = listOf(mood(0, 3), mood(1, 4), mood(2, 5), mood(4, 2))
        assertEquals(3, Metrics.moodStreak(moods, today))
    }

    @Test
    fun streakStartsYesterdayWhenTodayIsNotLogged() {
        val moods = listOf(mood(1, 4), mood(2, 5))
        assertEquals(2, Metrics.moodStreak(moods, today))
        assertEquals(0, Metrics.moodStreak(listOf(mood(3, 1)), today))
    }

    @Test
    fun recentAverageCoversTheLastSevenDays() {
        val moods = listOf(mood(0, 5), mood(6, 3), mood(7, 1))
        assertEquals(4.0, Metrics.recentMoodAverage(moods, today)!!, 1e-9)
        assertNull(Metrics.recentMoodAverage(listOf(mood(10, 2)), today))
    }

    @Test
    fun distributionCountsEachLevel() {
        val moods = listOf(mood(0, 5), mood(1, 5), mood(2, 1))
        assertEquals(listOf(1, 0, 0, 0, 2), Metrics.moodDistribution(moods))
    }

    @Test
    fun historiesGroupByItemAndUseCurrentNames() {
        val first = CheckIn(
            id = "a", date = Instant.ofEpochSecond(1_000), note = "", cycleId = null,
            ratings = listOf(
                ItemRating("guitar", "Guitar", ItemKind.HOBBY, 3, 4, 2),
                ItemRating("water", "Water", ItemKind.GOOD_HABIT, adherence = 2),
            ),
        )
        val second = CheckIn(
            id = "b", date = Instant.ofEpochSecond(2_000), note = "", cycleId = null,
            ratings = listOf(ItemRating("guitar", "Guitar", ItemKind.HOBBY, 5, 5, 5)),
        )
        val items = listOf(TrackedItem("guitar", "Bass guitar", ItemKind.HOBBY, Instant.EPOCH))

        val hobbies = Metrics.histories(listOf(first, second), items, setOf(ItemKind.HOBBY))
        assertEquals(1, hobbies.size)
        assertEquals("Bass guitar", hobbies[0].name)
        assertEquals(2, hobbies[0].samples.size)

        val habits = Metrics.histories(listOf(first, second), items, ItemKind.habitKinds.toSet())
        assertEquals("Water", habits.single().name)

        assertEquals(2.0, Metrics.averageAdherence(listOf(first, second))!!, 1e-9)
        assertEquals(4.5, Metrics.averageEnjoyment(listOf(first, second))!!, 1e-9)

        val trend = Metrics.trendPoints(listOf(first, second))
        assertEquals(3, trend.size)
        assertEquals(3.0, trend[0].value, 1e-9)
        assertEquals(Metrics.HABITS_SERIES, trend[1].series)
    }
}
