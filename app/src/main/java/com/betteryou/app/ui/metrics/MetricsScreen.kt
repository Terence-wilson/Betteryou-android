package com.betteryou.app.ui.metrics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.betteryou.app.domain.CheckIn
import com.betteryou.app.domain.ItemHistory
import com.betteryou.app.domain.ItemKind
import com.betteryou.app.domain.Metrics
import com.betteryou.app.domain.MoodEntry
import com.betteryou.app.domain.MoodScale
import com.betteryou.app.domain.TrackedItem
import com.betteryou.app.domain.today
import com.betteryou.app.ui.components.AppCard
import com.betteryou.app.ui.components.BarChart
import com.betteryou.app.ui.components.BarEntry
import com.betteryou.app.ui.components.ChartPoint
import com.betteryou.app.ui.components.ChartSeries
import com.betteryou.app.ui.components.EmptyState
import com.betteryou.app.ui.components.LineChart
import com.betteryou.app.ui.components.formatShortDate
import com.betteryou.app.ui.components.icon
import com.betteryou.app.ui.components.scrollingScreen
import com.betteryou.app.ui.components.tint
import com.betteryou.app.ui.theme.Blue
import com.betteryou.app.ui.theme.Green
import com.betteryou.app.ui.theme.Orange
import com.betteryou.app.ui.theme.Pink
import com.betteryou.app.ui.theme.Purple
import com.betteryou.app.ui.theme.Red
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class MetricsTab(val title: String) {
    OVERVIEW("Overview"),
    HOBBIES("Hobbies"),
    HABITS("Habits"),
    MOOD("Mood"),
}

@Composable
fun MetricsScreen(
    checkIns: List<CheckIn>,
    moods: List<MoodEntry>,
    items: List<TrackedItem>,
    onUpdateCheckInNote: (CheckIn, String) -> Unit,
    onDeleteCheckIn: (CheckIn) -> Unit,
    onSaveMood: (LocalDate, Int, String) -> Unit,
    onDeleteMood: (LocalDate) -> Unit,
    contentPadding: PaddingValues,
) {
    var tab by rememberSaveable { mutableStateOf(MetricsTab.OVERVIEW) }

    Column(Modifier.scrollingScreen(contentPadding), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TabRow(selectedTabIndex = tab.ordinal, containerColor = Color.Transparent) {
            MetricsTab.entries.forEach { entry ->
                Tab(
                    selected = tab == entry,
                    onClick = { tab = entry },
                    text = { Text(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }

        when (tab) {
            MetricsTab.OVERVIEW -> {
                Overview(checkIns, moods)
                CheckInHistoryCard(checkIns, onUpdateCheckInNote, onDeleteCheckIn)
            }
            MetricsTab.HOBBIES -> {
                val histories = remember(checkIns, items) { Metrics.histories(checkIns, items, setOf(ItemKind.HOBBY)) }
                if (histories.isEmpty()) {
                    EmptyState(
                        "No hobby ratings yet",
                        "Rate your focused hobbies at your next check-in to track consistency, enjoyment and improvement.",
                        Icons.Filled.Insights,
                    )
                }
                histories.forEach { HobbyHistoryCard(it) }
            }
            MetricsTab.HABITS -> {
                val histories = remember(checkIns, items) {
                    Metrics.histories(checkIns, items, ItemKind.habitKinds.toSet())
                }
                if (histories.isEmpty()) {
                    EmptyState(
                        "No habit ratings yet",
                        "At each check-in you'll rate how well you followed your habits after the reset.",
                        Icons.Filled.Insights,
                    )
                }
                histories.forEach { HabitHistoryCard(it) }
            }
            MetricsTab.MOOD -> {
                MoodSection(moods)
                if (moods.isNotEmpty()) MoodEntriesCard(moods, onSaveMood, onDeleteMood)
            }
        }
    }
}

private fun formatScore(value: Double?): String = value?.let { String.format(Locale.getDefault(), "%.1f / 5", it) } ?: "—"

@Composable
private fun Overview(checkIns: List<CheckIn>, moods: List<MoodEntry>) {
    val date = today()
    val moodAverage = Metrics.recentMoodAverage(moods, date)
    val streak = Metrics.moodStreak(moods, date)

    val tiles = listOf(
        Tile("Check-ins", "${checkIns.size}", Icons.Filled.TaskAlt, MaterialTheme.colorScheme.primary),
        Tile("Habit adherence", formatScore(Metrics.averageAdherence(checkIns)), Icons.Filled.Verified, Green),
        Tile("Hobby enjoyment", formatScore(Metrics.averageEnjoyment(checkIns)), Icons.Filled.Favorite, Purple),
        Tile(
            "Mood (7 days)",
            moodAverage?.let { "${MoodScale.emoji(Math.round(it).toInt())} ${String.format(Locale.getDefault(), "%.1f", it)}" } ?: "—",
            Icons.Filled.Mood,
            Orange,
        ),
        Tile("Mood streak", "$streak ${if (streak == 1) "day" else "days"}", Icons.Filled.LocalFireDepartment, Red),
        Tile("Days logged", "${moods.size}", Icons.Filled.CalendarMonth, Blue),
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { StatTile(it, Modifier.weight(1f)) }
            }
        }
    }

    AppCard {
        Text("Check-in scores", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Average hobby score and habit adherence at each check-in.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val points = Metrics.trendPoints(checkIns)
        if (points.isEmpty()) {
            EmptyChart("Complete your first check-in to see trends.")
        } else {
            val series = listOf(Metrics.HOBBIES_SERIES to Purple, Metrics.HABITS_SERIES to Green).map { (name, color) ->
                ChartSeries(name, color, points.filter { it.series == name }.map { ChartPoint(it.date, it.value) })
            }.filter { it.points.isNotEmpty() }
            LineChart(series, showLegend = true, description = "Check-in scores over time")
        }
    }
}

private data class Tile(val title: String, val value: String, val icon: ImageVector, val tint: Color)

@Composable
private fun StatTile(tile: Tile, modifier: Modifier = Modifier) {
    AppCard(modifier) {
        Icon(tile.icon, contentDescription = null, tint = tile.tint)
        Text(
            tile.value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(tile.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyChart(message: String) {
    Text(
        message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
            .wrapContentHeight(Alignment.CenterVertically),
    )
}

@Composable
private fun HistoryHeader(history: ItemHistory, trailing: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(history.kind.icon, contentDescription = null, tint = history.kind.tint, modifier = Modifier.size(20.dp))
        Text(
            history.name,
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = history.kind.tint,
        )
        Text(trailing, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HobbyHistoryCard(history: ItemHistory) {
    val latest = history.samples.lastOrNull()?.rating
    val count = history.samples.size
    AppCard {
        HistoryHeader(history, "$count check-in${if (count == 1) "" else "s"}")
        Row {
            Score("Consistency", latest?.consistency, Modifier.weight(1f))
            Score("Enjoyment", latest?.enjoyment, Modifier.weight(1f))
            Score("Improvement", latest?.improvement, Modifier.weight(1f))
        }
        val series = listOf(
            ChartSeries("Consistency", Blue, history.samples.mapNotNull { s -> s.rating.consistency?.let { ChartPoint(s.date, it.toDouble()) } }),
            ChartSeries("Enjoyment", Pink, history.samples.mapNotNull { s -> s.rating.enjoyment?.let { ChartPoint(s.date, it.toDouble()) } }),
            ChartSeries("Improvement", Purple, history.samples.mapNotNull { s -> s.rating.improvement?.let { ChartPoint(s.date, it.toDouble()) } }),
        )
        LineChart(series, height = 180.dp, showLegend = true, description = "${history.name} ratings over time")
    }
}

@Composable
private fun Score(title: String, value: Int?, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value?.toString() ?: "—", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HabitHistoryCard(history: ItemHistory) {
    val values = history.samples.mapNotNull { it.rating.adherence }
    val average = if (values.isEmpty()) null else values.average()
    AppCard {
        HistoryHeader(history, average?.let { String.format(Locale.getDefault(), "Avg %.1f / 5", it) } ?: "")
        Text(
            history.kind.adherenceQuestion,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val lastReset = history.samples.lastOrNull { it.rating.resetDaysTotal != null }?.rating
        if (lastReset != null) {
            Text(
                "Last reset: kept ${lastReset.resetDaysKept ?: 0} of ${lastReset.resetDaysTotal} days",
                style = MaterialTheme.typography.labelMedium,
                color = history.kind.tint,
            )
        }
        // Show the most recent check-ins so bars stay readable.
        val entries = history.samples.takeLast(10).mapNotNull { sample ->
            sample.rating.adherence?.let { BarEntry(formatShortDate(sample.date), it.toDouble(), history.kind.tint, "$it") }
        }
        if (entries.isEmpty()) {
            EmptyChart("Not rated at a check-in yet.")
        } else {
            BarChart(entries, maxValue = 5.0, height = 150.dp, description = "${history.name} adherence by check-in")
        }
    }
}


@Composable
private fun MoodSection(moods: List<MoodEntry>) {
    if (moods.isEmpty()) {
        EmptyState(
            "No mood logs yet",
            "Log how you feel from the Today tab. Turn on the daily check-in in Settings if it's hidden.",
            Icons.Filled.Mood,
        )
        return
    }
    val recent = Metrics.recentMoods(moods, today(), 30)

    AppCard {
        Text("Last 30 days", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (recent.isEmpty()) {
            EmptyChart("No entries in the last 30 days.")
        } else {
            val zone = ZoneId.systemDefault()
            val series = ChartSeries(
                "Mood",
                Orange,
                recent.map { ChartPoint(it.day.atStartOfDay(zone).toInstant(), it.mood.toDouble()) },
            )
            val windowStart = today().minusDays(29).atStartOfDay(zone).toInstant()
            LineChart(
                listOf(series),
                yLabel = { MoodScale.emoji(it) },
                xRange = windowStart to today().atStartOfDay(zone).toInstant(),
                description = "Mood over the last 30 days",
            )
        }
    }

    AppCard {
        Text("How you've felt", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        val distribution = Metrics.moodDistribution(moods)
        val entries = distribution.mapIndexed { index, count ->
            BarEntry(MoodScale.emoji(index + 1), count.toDouble(), Orange, "$count")
        }
        BarChart(entries, maxValue = (distribution.maxOrNull() ?: 0).toDouble(), description = "Days logged at each mood")
    }
}
