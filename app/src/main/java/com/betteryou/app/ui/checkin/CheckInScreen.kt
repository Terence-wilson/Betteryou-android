@file:OptIn(ExperimentalMaterial3Api::class)

package com.betteryou.app.ui.checkin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.betteryou.app.domain.AppSettings
import com.betteryou.app.domain.Cycle
import com.betteryou.app.domain.HobbyScores
import com.betteryou.app.domain.ItemKind
import com.betteryou.app.domain.Schedule
import com.betteryou.app.domain.TrackedItem
import com.betteryou.app.ui.components.AllFocusPickers
import com.betteryou.app.ui.components.FormSection
import com.betteryou.app.ui.components.LabeledRow
import com.betteryou.app.ui.components.RatingRow
import com.betteryou.app.ui.components.icon
import com.betteryou.app.ui.components.scrollingScreen
import com.betteryou.app.ui.components.tint
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private enum class CheckInStep(val title: String) {
    HOBBIES("Rate Your Hobbies"),
    HABITS("Rate Your Habits"),
    FOCUS("Next Focus"),
    REVIEW("Wrap Up"),
}

private enum class HobbyMetric(val key: String, val title: String, val caption: String, val low: String, val high: String) {
    CONSISTENCY("c", "Consistency", "How consistently did you do it?", "Rarely", "Very consistently"),
    ENJOYMENT("e", "Enjoyment", "How much did you enjoy it?", "Not at all", "Loved it"),
    IMPROVEMENT("i", "Improvement", "How much did your skill improve?", "No change", "Big progress"),
}

private val mediumDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

private fun formatDate(instant: Instant): String = mediumDate.format(instant.atZone(ZoneId.systemDefault()))

/** The periodic check-in: rate hobbies, rate habits after the reset, then choose the next focus. */
@Composable
fun CheckInScreen(
    cycle: Cycle,
    settings: AppSettings,
    items: List<TrackedItem>,
    onAddItem: (String, ItemKind) -> String,
    onComplete: (hobbyScores: Map<String, HobbyScores>, adherence: Map<String, Int>, note: String, nextFocus: Set<String>) -> Unit,
    onCancel: () -> Unit,
) {
    // Draft state is kept in plain (Bundle-friendly) types so it survives rotation.
    var stepIndex by rememberSaveable(cycle.id) { mutableIntStateOf(0) }
    var scores by rememberSaveable(cycle.id) { mutableStateOf(mapOf<String, Int>()) }
    var nextFocus by rememberSaveable(cycle.id) { mutableStateOf(cycle.focusItemIds) }
    var note by rememberSaveable(cycle.id) { mutableStateOf("") }

    val focusedHobbies = cycle.focused(items, ItemKind.HOBBY)
    val focusedHabits = ItemKind.habitKinds.flatMap { cycle.focused(items, it) }
    val steps = buildList {
        if (focusedHobbies.isNotEmpty()) add(CheckInStep.HOBBIES)
        if (focusedHabits.isNotEmpty()) add(CheckInStep.HABITS)
        add(CheckInStep.FOCUS)
        add(CheckInStep.REVIEW)
    }
    val index = stepIndex.coerceIn(0, steps.lastIndex)
    val step = steps[index]
    val isLastStep = index == steps.lastIndex

    fun score(key: String) = scores[key] ?: 3
    fun setScore(key: String, value: Int) {
        scores = scores + (key to value)
    }

    fun complete() {
        val hobbyScores = focusedHobbies.associate { item ->
            item.id to HobbyScores(
                consistency = score("${item.id}:${HobbyMetric.CONSISTENCY.key}"),
                enjoyment = score("${item.id}:${HobbyMetric.ENJOYMENT.key}"),
                improvement = score("${item.id}:${HobbyMetric.IMPROVEMENT.key}"),
            )
        }
        val adherence = focusedHabits.associate { it.id to score("${it.id}:a") }
        onComplete(hobbyScores, adherence, note, nextFocus)
    }

    BackHandler { if (index > 0) stepIndex = index - 1 else onCancel() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(step.title) },
                    navigationIcon = {
                        IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, contentDescription = "Cancel") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
                LinearProgressIndicator(
                    progress = { (index + 1f) / steps.size },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (index > 0) {
                        OutlinedButton(
                            onClick = { stepIndex = index - 1 },
                            modifier = Modifier.weight(1f).height(52.dp),
                        ) { Text("Back") }
                    }
                    Button(
                        onClick = { if (isLastStep) complete() else stepIndex = index + 1 },
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) { Text(if (isLastStep) "Complete check-in" else "Next") }
                }
            }
        },
    ) { padding ->
        Column(Modifier.scrollingScreen(padding), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            when (step) {
                CheckInStep.HOBBIES -> {
                    Hint("Rate each hobby you focused on this cycle from 1 to 5.")
                    focusedHobbies.forEach { item ->
                        FormSection(title = item.name, icon = ItemKind.HOBBY.icon, tint = ItemKind.HOBBY.tint) {
                            HobbyMetric.entries.forEachIndexed { i, metric ->
                                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                val key = "${item.id}:${metric.key}"
                                RatingRow(
                                    title = metric.title,
                                    caption = metric.caption,
                                    lowLabel = metric.low,
                                    highLabel = metric.high,
                                    tint = ItemKind.HOBBY.tint,
                                    value = score(key),
                                    onValueChange = { setScore(key, it) },
                                )
                            }
                        }
                    }
                }

                CheckInStep.HABITS -> {
                    Hint(
                        if (cycle.hasPostResetPeriod) {
                            "Rate how well you followed each habit after your reset ended " +
                                "(${formatDate(cycle.resetEndDate)}) up until now."
                        } else {
                            "Your whole cycle was a reset this time. Rate how well you followed each habit during it."
                        },
                    )
                    ItemKind.habitKinds.forEach { kind ->
                        val list = cycle.focused(items, kind)
                        if (list.isNotEmpty()) {
                            FormSection(title = kind.title, icon = kind.icon, tint = kind.tint) {
                                list.forEachIndexed { i, item ->
                                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    val key = "${item.id}:a"
                                    RatingRow(
                                        title = item.name,
                                        caption = kind.adherenceQuestion,
                                        lowLabel = kind.adherenceLowLabel,
                                        highLabel = kind.adherenceHighLabel,
                                        tint = kind.tint,
                                        value = score(key),
                                        onValueChange = { setScore(key, it) },
                                    )
                                }
                            }
                        }
                    }
                }

                CheckInStep.FOCUS -> {
                    Hint(
                        "Choose your focus for the next cycle. Your current picks are pre-selected — " +
                            "keep them to continue, or swap them out.",
                    )
                    OutlinedButton(onClick = { nextFocus = cycle.focusItemIds }, modifier = Modifier.fillMaxWidth()) {
                        Text("Keep my current focus")
                    }
                    AllFocusPickers(items, nextFocus, onSelectionChange = { nextFocus = it }, onAddItem = onAddItem)
                }

                CheckInStep.REVIEW -> {
                    FormSection(title = "Reflection") {
                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it },
                            placeholder = { Text("Anything you learned this cycle? (optional)") },
                            minLines = 3,
                            maxLines = 8,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    val next = Schedule.cycleDates(Instant.now(), settings.frequency, settings.customResetDays)
                    val validFocusCount = nextFocus.count { id -> items.any { it.id == id } }
                    FormSection(title = "Your next cycle") {
                        LabeledRow("Check-in", formatDate(next.checkIn))
                        LabeledRow("Reset ends", formatDate(next.resetEnd))
                        LabeledRow("Focus items", "$validFocusCount")
                    }
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}
