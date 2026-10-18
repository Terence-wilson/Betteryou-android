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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.betteryou.app.domain.AppSettings
import com.betteryou.app.domain.Cycle
import com.betteryou.app.domain.HabitLog
import com.betteryou.app.domain.HabitRatingPeriod
import com.betteryou.app.domain.HobbyScores
import com.betteryou.app.domain.ItemKind
import com.betteryou.app.domain.Schedule
import com.betteryou.app.domain.TrackedItem
import com.betteryou.app.domain.resetProgress
import com.betteryou.app.domain.today
import com.betteryou.app.ui.AddResult
import com.betteryou.app.ui.components.AllFocusPickers
import com.betteryou.app.ui.components.ConfirmDialog
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

/** The periodic check-in: rate hobbies, rate habits, then choose the next focus. */
@Composable
fun CheckInScreen(
    cycle: Cycle,
    settings: AppSettings,
    items: List<TrackedItem>,
    habitLogs: List<HabitLog>,
    onAddItem: (String, ItemKind) -> AddResult,
    onComplete: (hobbyScores: Map<String, HobbyScores>, adherence: Map<String, Int?>, note: String, nextFocus: Set<String>) -> Unit,
    onCancel: () -> Unit,
) {
    // Draft state is kept in plain (Bundle-friendly) types so it survives rotation.
    var stepIndex by rememberSaveable(cycle.id) { mutableIntStateOf(0) }
    var scores by rememberSaveable(cycle.id) { mutableStateOf(mapOf<String, Int>()) }
    var nextFocus by rememberSaveable(cycle.id) { mutableStateOf(cycle.focusItemIds) }
    var note by rememberSaveable(cycle.id) { mutableStateOf("") }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var confirmUnrated by rememberSaveable { mutableStateOf(false) }
    // Rating period and reset record are fixed when the check-in opens.
    val period = remember(cycle.id) { cycle.habitRatingPeriod() }
    val day = remember(cycle.id) { today() }

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

    fun hobbyKey(item: TrackedItem, metric: HobbyMetric) = "${item.id}:${metric.key}"
    fun habitKey(item: TrackedItem) = "${item.id}:a"
    fun setScore(key: String, value: Int?) {
        scores = if (value == null) scores - key else scores + (key to value)
    }

    /** Ratings on this step the user hasn't given. */
    val unratedOnStep = when (step) {
        CheckInStep.HOBBIES -> focusedHobbies.sumOf { item -> HobbyMetric.entries.count { hobbyKey(item, it) !in scores } }
        CheckInStep.HABITS -> focusedHabits.count { habitKey(it) !in scores }
        else -> 0
    }
    val hasDraft = scores.isNotEmpty() || note.isNotBlank() || nextFocus != cycle.focusItemIds || index > 0

    fun complete() {
        val hobbyScores = focusedHobbies.associate { item ->
            item.id to HobbyScores(
                consistency = scores[hobbyKey(item, HobbyMetric.CONSISTENCY)],
                enjoyment = scores[hobbyKey(item, HobbyMetric.ENJOYMENT)],
                improvement = scores[hobbyKey(item, HobbyMetric.IMPROVEMENT)],
            )
        }
        val adherence = focusedHabits.associate { it.id to scores[habitKey(it)] }
        onComplete(hobbyScores, adherence, note, nextFocus)
    }

    fun advance() {
        if (isLastStep) complete() else stepIndex = index + 1
    }

    val cancel: () -> Unit = { if (hasDraft) confirmDiscard = true else onCancel() }

    BackHandler { if (index > 0) stepIndex = index - 1 else cancel() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(step.title) },
                    navigationIcon = {
                        IconButton(onClick = cancel) { Icon(Icons.Filled.Close, contentDescription = "Cancel") }
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
                        onClick = { if (unratedOnStep > 0) confirmUnrated = true else advance() },
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) { Text(if (isLastStep) "Complete check-in" else "Next") }
                }
            }
        },
    ) { padding ->
        Column(Modifier.scrollingScreen(padding), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            when (step) {
                CheckInStep.HOBBIES -> {
                    Hint("Rate each hobby you focused on this cycle from 1 to 5. Tap a score again to clear it.")
                    focusedHobbies.forEach { item ->
                        FormSection(title = item.name, icon = ItemKind.HOBBY.icon, tint = ItemKind.HOBBY.tint) {
                            HobbyMetric.entries.forEachIndexed { i, metric ->
                                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                val key = hobbyKey(item, metric)
                                RatingRow(
                                    title = metric.title,
                                    caption = metric.caption,
                                    lowLabel = metric.low,
                                    highLabel = metric.high,
                                    tint = ItemKind.HOBBY.tint,
                                    value = scores[key],
                                    onValueChange = { setScore(key, it) },
                                )
                            }
                        }
                    }
                }

                CheckInStep.HABITS -> {
                    Hint(habitPeriodText(period, cycle))
                    ItemKind.habitKinds.forEach { kind ->
                        val list = cycle.focused(items, kind)
                        if (list.isNotEmpty()) {
                            FormSection(title = kind.title, icon = kind.icon, tint = kind.tint) {
                                list.forEachIndexed { i, item ->
                                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    val progress = resetProgress(cycle, item.id, habitLogs, day)
                                    if (progress.total > 0) {
                                        Text(
                                            "Reset checklist: kept ${progress.kept} of ${progress.total} days",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = kind.tint,
                                        )
                                    }
                                    val key = habitKey(item)
                                    RatingRow(
                                        title = item.name,
                                        caption = kind.adherenceQuestion,
                                        lowLabel = kind.adherenceLowLabel,
                                        highLabel = kind.adherenceHighLabel,
                                        tint = kind.tint,
                                        value = scores[key],
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
                    if (nextFocus != cycle.focusItemIds) {
                        OutlinedButton(onClick = { nextFocus = cycle.focusItemIds }, modifier = Modifier.fillMaxWidth()) {
                            Text("Reset to current focus")
                        }
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
                    FormSection(
                        title = "Your next cycle",
                        footer = "Your next cycle starts today, beginning with its reset.",
                    ) {
                        LabeledRow("Reset ends", formatDate(next.resetEnd))
                        LabeledRow("Check-in", formatDate(next.checkIn))
                        LabeledRow("Focus items", "$validFocusCount")
                    }
                }
            }
        }
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = "Discard this check-in?",
            message = "Your ratings and notes for this check-in will be lost.",
            confirmLabel = "Discard",
            dismissLabel = "Keep going",
            destructive = true,
            onConfirm = {
                confirmDiscard = false
                onCancel()
            },
            onDismiss = { confirmDiscard = false },
        )
    }

    if (confirmUnrated) {
        ConfirmDialog(
            title = "Leave $unratedOnStep ${if (unratedOnStep == 1) "rating" else "ratings"} blank?",
            message = "Blank ratings aren't counted in your progress.",
            confirmLabel = "Continue",
            dismissLabel = "Rate them",
            onConfirm = {
                confirmUnrated = false
                advance()
            },
            onDismiss = { confirmUnrated = false },
        )
    }
}

private fun habitPeriodText(period: HabitRatingPeriod, cycle: Cycle): String = when (period) {
    HabitRatingPeriod.DURING_RESET ->
        "You're checking in during your reset. Rate how well you've followed each habit since your cycle " +
            "started (${formatDate(cycle.startDate)})."
    HabitRatingPeriod.AFTER_RESET ->
        "Rate how well you followed each habit after your reset ended (${formatDate(cycle.resetEndDate)}) up until now."
    HabitRatingPeriod.WHOLE_CYCLE_RESET ->
        "Your whole cycle was a reset this time. Rate how well you followed each habit during it."
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
