package com.betteryou.app.domain

import java.time.Instant
import java.time.LocalDate

data class RatingSample(val date: Instant, val rating: ItemRating)

/** All ratings for one item across check-ins, oldest first. */
data class ItemHistory(
    val itemId: String,
    val name: String,
    val kind: ItemKind,
    val samples: List<RatingSample>,
)

data class MetricPoint(val date: Instant, val value: Double, val series: String)

/** Calculations behind the Progress tab. */
object Metrics {
    const val HOBBIES_SERIES = "Hobbies"
    const val HABITS_SERIES = "Habits"

    fun average(values: List<Double>): Double? = if (values.isEmpty()) null else values.sum() / values.size

    /** Groups ratings of [kinds] by item. [checkIns] must be sorted oldest first. */
    fun histories(checkIns: List<CheckIn>, items: List<TrackedItem>, kinds: Set<ItemKind>): List<ItemHistory> {
        val grouped = LinkedHashMap<String, MutableList<RatingSample>>()
        for (checkIn in checkIns) {
            for (rating in checkIn.ratings) {
                if (rating.kind in kinds) {
                    grouped.getOrPut(rating.itemId) { mutableListOf() }.add(RatingSample(checkIn.date, rating))
                }
            }
        }
        return grouped.mapNotNull { (id, samples) ->
            val last = samples.lastOrNull() ?: return@mapNotNull null
            val name = items.firstOrNull { it.id == id }?.name ?: last.rating.itemName
            ItemHistory(id, name, last.rating.kind, samples)
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    fun averageAdherence(checkIns: List<CheckIn>): Double? =
        average(checkIns.flatMap { it.ratings }.mapNotNull { it.adherence?.toDouble() })

    fun averageEnjoyment(checkIns: List<CheckIn>): Double? =
        average(checkIns.flatMap { it.ratings }.mapNotNull { it.enjoyment?.toDouble() })

    /** Entries from the last [days] days, including today. */
    fun recentMoods(moods: List<MoodEntry>, today: LocalDate, days: Int): List<MoodEntry> {
        val cutoff = today.minusDays((days - 1).toLong())
        return moods.filter { !it.day.isBefore(cutoff) && !it.day.isAfter(today) }
    }

    fun recentMoodAverage(moods: List<MoodEntry>, today: LocalDate, days: Int = 7): Double? =
        average(recentMoods(moods, today, days).map { it.mood.toDouble() })

    /** Consecutive days with a mood entry, ending today (or yesterday if today isn't logged yet). */
    fun moodStreak(moods: List<MoodEntry>, today: LocalDate): Int {
        val days = moods.map { it.day }.toSet()
        var day = if (today in days) today else today.minusDays(1)
        var streak = 0
        while (day in days) {
            streak++
            day = day.minusDays(1)
        }
        return streak
    }

    /** Number of days logged at each mood level, index 0 = mood 1. */
    fun moodDistribution(moods: List<MoodEntry>): List<Int> =
        (1..5).map { level -> moods.count { it.mood == level } }

    /** Average hobby score and habit adherence at each check-in. */
    fun trendPoints(checkIns: List<CheckIn>): List<MetricPoint> = checkIns.flatMap { checkIn ->
        buildList {
            average(checkIn.ratings.mapNotNull { it.hobbyAverage })?.let {
                add(MetricPoint(checkIn.date, it, HOBBIES_SERIES))
            }
            average(checkIn.ratings.mapNotNull { it.adherence?.toDouble() })?.let {
                add(MetricPoint(checkIn.date, it, HABITS_SERIES))
            }
        }
    }
}
