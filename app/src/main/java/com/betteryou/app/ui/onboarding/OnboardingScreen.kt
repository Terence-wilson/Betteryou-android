@file:OptIn(ExperimentalMaterial3Api::class)

package com.betteryou.app.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.betteryou.app.domain.AppSettings
import com.betteryou.app.domain.ItemKind
import com.betteryou.app.domain.TrackedItem
import com.betteryou.app.ui.components.AllFocusPickers
import com.betteryou.app.ui.components.ItemEditorSection
import com.betteryou.app.ui.components.scrollingScreen
import com.betteryou.app.ui.settings.NotificationSettingsSections
import com.betteryou.app.ui.settings.ScheduleSettingsSections
import com.betteryou.app.ui.settings.SettingsUpdater
import com.betteryou.app.ui.settings.rememberNotificationPermissionRequester

private enum class OnboardingStep(val title: String) {
    WELCOME("Welcome"),
    SCHEDULE("Your Schedule"),
    HOBBIES("Hobbies & Interests"),
    GOOD_HABITS("Good Habits"),
    BAD_HABITS("Bad Habits"),
    BREAKS("Breaks"),
    FOCUS("Your First Focus"),
    NOTIFICATIONS("Reminders"),
}

@Composable
fun OnboardingScreen(
    settings: AppSettings,
    items: List<TrackedItem>,
    onUpdateSettings: SettingsUpdater,
    onAddItem: (String, ItemKind) -> String,
    onRenameItem: (TrackedItem, String) -> Unit,
    onDeleteItem: (TrackedItem) -> Unit,
    onFinish: (Set<String>) -> Unit,
) {
    var stepIndex by rememberSaveable { mutableIntStateOf(0) }
    var focus by rememberSaveable { mutableStateOf(setOf<String>()) }
    val steps = OnboardingStep.entries
    val step = steps[stepIndex]
    val isLastStep = stepIndex == steps.lastIndex
    val requestPermission = rememberNotificationPermissionRequester()

    BackHandler(enabled = stepIndex > 0) { stepIndex-- }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = { Text(step.title) },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
                LinearProgressIndicator(
                    progress = { (stepIndex + 1f) / steps.size },
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
                    if (stepIndex > 0) {
                        OutlinedButton(
                            onClick = { stepIndex-- },
                            modifier = Modifier.weight(1f).height(52.dp),
                        ) { Text("Back") }
                    }
                    Button(
                        onClick = {
                            if (isLastStep) {
                                if (settings.checkInRemindersEnabled ||
                                    (settings.dailyCheckInEnabled && settings.dailyReminderEnabled)
                                ) {
                                    requestPermission()
                                }
                                onFinish(focus.filter { id -> items.any { it.id == id } }.toSet())
                            } else {
                                stepIndex++
                            }
                        },
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) { Text(if (isLastStep) "Start my first cycle" else "Continue") }
                }
            }
        },
    ) { padding ->
        Column(Modifier.scrollingScreen(padding), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            val editorKind = when (step) {
                OnboardingStep.HOBBIES -> ItemKind.HOBBY
                OnboardingStep.GOOD_HABITS -> ItemKind.GOOD_HABIT
                OnboardingStep.BAD_HABITS -> ItemKind.BAD_HABIT
                OnboardingStep.BREAKS -> ItemKind.BREAK_ACTIVITY
                else -> null
            }

            when (step) {
                OnboardingStep.WELCOME -> WelcomeStep()
                OnboardingStep.SCHEDULE -> ScheduleSettingsSections(settings, onUpdateSettings)
                OnboardingStep.HOBBIES,
                OnboardingStep.GOOD_HABITS,
                OnboardingStep.BAD_HABITS,
                OnboardingStep.BREAKS,
                -> if (editorKind != null) {
                    key(editorKind) {
                        ItemEditorSection(
                            kind = editorKind,
                            items = items.filter { it.kind == editorKind },
                            onAdd = { onAddItem(it, editorKind) },
                            onRename = onRenameItem,
                            onDelete = onDeleteItem,
                            showsSuggestions = true,
                        )
                    }
                }
                OnboardingStep.FOCUS -> {
                    Text(
                        "Choose what to focus on for your first cycle. You can keep or change these at every check-in.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AllFocusPickers(items, focus, onSelectionChange = { focus = it }, onAddItem = onAddItem)
                }
                OnboardingStep.NOTIFICATIONS -> NotificationSettingsSections(settings, onUpdateSettings)
            }
        }
    }
}

@Composable
private fun WelcomeStep() {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Icon(
            Icons.Filled.Eco,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(80.dp)
                .align(Alignment.CenterHorizontally),
        )
        Text(
            "Become a better you, one check-in at a time.",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Feature(
            Icons.Filled.Autorenew,
            "Cycles & check-ins",
            "Pick how often you check in. Each check-in you rate your progress and choose your next focus.",
        )
        Feature(
            Icons.Filled.Bolt,
            "Reset period",
            "Each cycle starts with a reset: stop bad habits entirely and do good habits every day.",
        )
        Feature(
            Icons.Filled.Palette,
            "Grow your hobbies",
            "Focus on 3 hobbies at a time and track consistency, enjoyment and improvement.",
        )
        Feature(
            Icons.Filled.Insights,
            "See your progress",
            "Charts and stats show how you're doing over time, including an optional daily mood log.",
        )
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.width(36.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
