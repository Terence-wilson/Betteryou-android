package com.betteryou.app.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.betteryou.app.domain.Cycle
import com.betteryou.app.domain.CyclePhase
import com.betteryou.app.domain.HabitLog
import com.betteryou.app.domain.ItemKind
import com.betteryou.app.domain.MoodEntry
import com.betteryou.app.domain.MoodScale
import com.betteryou.app.domain.TrackedItem
import com.betteryou.app.domain.resetProgress
import com.betteryou.app.ui.components.AppCard
import com.betteryou.app.ui.components.ConfirmDialog
import com.betteryou.app.ui.components.CycleProgressBar
import com.betteryou.app.ui.components.Dot
import com.betteryou.app.ui.components.EmptyState
import com.betteryou.app.ui.components.dayText
import com.betteryou.app.ui.components.formatShortDate
import com.betteryou.app.ui.components.icon
import com.betteryou.app.ui.components.scrollingScreen
import com.betteryou.app.ui.components.tint
import com.betteryou.app.ui.theme.Orange
import com.betteryou.app.ui.theme.Pink
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The current time, refreshed every minute and whenever the app comes back to the foreground. */
@Composable
fun rememberNow(): Instant {
    var resumes by rememberSaveable { mutableStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumes++
        onPauseOrDispose { }
    }
    val now by produceState(Instant.now(), resumes) {
        value = Instant.now()
        while (true) {
            delay(60_000)
            value = Instant.now()
        }
    }
    return now
}

@Composable
fun DashboardScreen(
    cycle: Cycle?,
    items: List<TrackedItem>,
    habitLogs: List<HabitLog>,
    dailyCheckInEnabled: Boolean,
    todaysMood: MoodEntry?,
    onStartCheckIn: () -> Unit,
    onEditFocus: () -> Unit,
    onOpenMood: () -> Unit,
    onStartCycle: () -> Unit,
    onSetHabitKept: (String, Boolean) -> Unit,
    contentPadding: PaddingValues,
) {
    val now = rememberNow()
    Column(Modifier.scrollingScreen(contentPadding), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (cycle == null) {
            EmptyState(
                title = "No active cycle",
                message = "Start a new cycle to begin your reset.",
                icon = Icons.Filled.Autorenew,
            )
            Button(onClick = onStartCycle, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Start a cycle")
            }
        } else {
            ActiveCycleContent(
                cycle, items, habitLogs, dailyCheckInEnabled, todaysMood, now,
                onStartCheckIn, onEditFocus, onOpenMood, onSetHabitKept,
            )
        }
    }
}

@Composable
private fun ActiveCycleContent(
    cycle: Cycle,
    items: List<TrackedItem>,
    habitLogs: List<HabitLog>,
    dailyCheckInEnabled: Boolean,
    todaysMood: MoodEntry?,
    now: Instant,
    onStartCheckIn: () -> Unit,
    onEditFocus: () -> Unit,
    onOpenMood: () -> Unit,
    onSetHabitKept: (String, Boolean) -> Unit,
) {
    val phase = cycle.phase(now)
    var confirmEarly by rememberSaveable { mutableStateOf(false) }

    CycleStatusCard(cycle, phase, now)

    if (phase == CyclePhase.CheckInDue) {
        Button(onClick = onStartCheckIn, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Icon(Icons.Filled.TaskAlt, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Start your check-in")
        }
    }

    HabitChecklistCard(
        cycle, items, habitLogs,
        day = now.atZone(ZoneId.systemDefault()).toLocalDate(),
        inReset = phase.isReset,
        onSetKept = onSetHabitKept,
    )

    if (dailyCheckInEnabled) MoodCard(todaysMood, onOpenMood)

    FocusSummaryCard(cycle, items, inReset = phase.isReset, onEdit = onEditFocus)

    if (phase != CyclePhase.CheckInDue) {
        // Ending a cycle early is a deliberate, secondary action.
        TextButton(onClick = { confirmEarly = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Check in early")
        }
    }

    if (confirmEarly) {
        ConfirmDialog(
            title = "Check in early?",
            message = "Checking in now ends this cycle " +
                "${if (phase.isReset) "before your reset is over" else "before ${formatShortDate(cycle.checkInDate)}"} " +
                "and starts the next one today.",
            confirmLabel = "Check in now",
            onConfirm = {
                confirmEarly = false
                onStartCheckIn()
            },
            onDismiss = { confirmEarly = false },
        )
    }
}

/** Daily tick-off for focused habits. During the reset this is how the reset itself gets tracked. */
@Composable
private fun HabitChecklistCard(
    cycle: Cycle,
    items: List<TrackedItem>,
    habitLogs: List<HabitLog>,
    day: LocalDate,
    inReset: Boolean,
    onSetKept: (String, Boolean) -> Unit,
) {
    val habits = ItemKind.habitKinds.flatMap { cycle.focused(items, it) }
    if (habits.isEmpty()) return

    AppCard {
        Text(
            if (inReset) "Today's reset checklist" else "Today's checklist",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            if (inReset) {
                "Check off each habit you kept today. Your reset record is shown at your check-in."
            } else {
                "Optional: keep checking off habits to stay on track until your check-in."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        habits.forEachIndexed { index, item ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val kept = habitLogs.any { it.itemId == item.id && it.day == day }
            Row(
                Modifier
                    .fillMaxWidth()
                    .toggleable(value = kept, role = Role.Checkbox, onValueChange = { onSetKept(item.id, it) })
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    if (kept) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (kept) item.kind.tint else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(Modifier.weight(1f)) {
                    Text(item.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        checklistPrompt(item.kind),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (inReset) {
                    val progress = resetProgress(cycle, item.id, habitLogs, day)
                    Text(
                        "${progress.kept}/${progress.total}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun checklistPrompt(kind: ItemKind): String = when (kind) {
    ItemKind.GOOD_HABIT -> "Did it today"
    ItemKind.BAD_HABIT -> "Stayed away today"
    ItemKind.BREAK_ACTIVITY -> "Kept my break today"
    ItemKind.HOBBY -> "Practiced today"
}

@Composable
private fun MoodCard(todaysMood: MoodEntry?, onClick: () -> Unit) {
    AppCard(Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(todaysMood?.let { MoodScale.emoji(it.mood) } ?: "🌤️", fontSize = 34.sp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Daily check-in", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (todaysMood != null) {
                        "Today you're feeling ${MoodScale.label(todaysMood.mood).lowercase()}. Tap to update."
                    } else {
                        "How are you feeling today?"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Icon(
                Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun CycleStatusCard(cycle: Cycle, phase: CyclePhase, now: Instant) {
    val (icon, tint) = phaseStyle(phase)
    AppCard {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(32.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(phaseTitle(phase), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    phaseMessage(phase, cycle.hasPostResetPeriod),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        CycleProgressBar(progress = cycle.progress(now), resetFraction = cycle.resetFraction)

        Row(Modifier.fillMaxWidth()) {
            DateColumn("Started", cycle.startDate, Alignment.Start)
            Spacer(Modifier.weight(1f))
            if (cycle.hasPostResetPeriod) {
                DateColumn("Reset ends", cycle.resetEndDate, Alignment.CenterHorizontally)
                Spacer(Modifier.weight(1f))
            }
            DateColumn("Check-in", cycle.checkInDate, Alignment.End)
        }
    }
}

@Composable
private fun DateColumn(label: String, date: Instant, alignment: Alignment.Horizontal) {
    Column(horizontalAlignment = alignment, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(formatShortDate(date), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

private fun phaseTitle(phase: CyclePhase): String = when (phase) {
    is CyclePhase.Reset -> "Reset in progress"
    is CyclePhase.Maintain -> "Keep it going"
    CyclePhase.CheckInDue -> "Check-in time"
}

private fun phaseMessage(phase: CyclePhase, hasPostResetPeriod: Boolean): String = when (phase) {
    is CyclePhase.Reset -> {
        val suffix = if (hasPostResetPeriod) "left in your reset" else "until your check-in"
        "${dayText(phase.daysLeft)} $suffix. No bad habits or break activities, and do your good habits every day."
    }
    is CyclePhase.Maintain ->
        "Reset complete! ${dayText(phase.daysLeft)} until your check-in. Keep your progress going."
    CyclePhase.CheckInDue ->
        "Your cycle is complete. Rate how it went and choose your next focus."
}

@Composable
private fun phaseStyle(phase: CyclePhase): Pair<ImageVector, Color> = when (phase) {
    is CyclePhase.Reset -> Icons.Filled.Bolt to Orange
    is CyclePhase.Maintain -> Icons.Filled.SelfImprovement to MaterialTheme.colorScheme.primary
    CyclePhase.CheckInDue -> Icons.Filled.NotificationsActive to Pink
}

@Composable
fun FocusSummaryCard(cycle: Cycle, items: List<TrackedItem>, inReset: Boolean, onEdit: () -> Unit) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "This cycle's focus",
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            TextButton(onClick = onEdit) { Text("Edit") }
        }

        val kinds = ItemKind.entries.filter { cycle.focused(items, it).isNotEmpty() }
        if (kinds.isEmpty()) {
            Text(
                "You're not focusing on anything yet. Tap Edit to pick up to 3 hobbies, good habits and bad habits.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        kinds.forEach { kind ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(kind.icon, contentDescription = null, tint = kind.tint, modifier = Modifier.size(18.dp))
                    Text(kind.title, style = MaterialTheme.typography.titleSmall, color = kind.tint)
                }
                Text(
                    kind.goal(inReset),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                cycle.focused(items, kind).forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Dot(kind.tint)
                        Text(item.name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}
