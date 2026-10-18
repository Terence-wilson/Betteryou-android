@file:OptIn(ExperimentalMaterial3Api::class)

package com.betteryou.app.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.betteryou.app.domain.AppSettings
import com.betteryou.app.domain.Cycle
import com.betteryou.app.domain.CyclePhase
import com.betteryou.app.domain.FrequencyUnit
import com.betteryou.app.domain.Schedule
import com.betteryou.app.ui.components.FormSection
import com.betteryou.app.ui.components.LabeledRow
import com.betteryou.app.ui.components.StepperRow
import com.betteryou.app.ui.components.SwitchRow
import com.betteryou.app.ui.components.dayText
import com.betteryou.app.notifications.Reminders
import com.betteryou.app.ui.theme.Orange
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

typealias SettingsUpdater = ((AppSettings) -> AppSettings) -> Unit

/** Returns a function that asks for notification permission on Android 13+ if it isn't granted yet. */
@Composable
fun rememberNotificationPermissionRequester(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return remember(context, launcher) {
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

/** Check-in frequency and reset length controls. Shared by onboarding and Settings. */
@Composable
fun ScheduleSettingsSections(settings: AppSettings, onUpdate: SettingsUpdater, activeCycle: Cycle? = null) {
    val cycleDays = maxOf(1, settings.frequency.lengthInDays())
    val defaultReset = Schedule.defaultResetDays(cycleDays)
    val effectiveReset = Schedule.resetDays(cycleDays, settings.customResetDays)

    FormSection(
        title = "Check-in frequency",
        footer = "At each check-in you rate your progress and choose what to focus on next. " +
            "That's about ${dayText(cycleDays)} per cycle.",
    ) {
        StepperRow(
            label = "Check in every",
            valueText = settings.frequency.displayText,
            canDecrease = settings.frequencyValue > 1,
            canIncrease = settings.frequencyValue < settings.frequencyUnit.maxValue,
            // Relative steps are applied to the stored value, so holding the button never uses a stale one.
            onStep = { delta -> onUpdate { it.copy(frequencyValue = it.frequencyValue + delta) } },
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            FrequencyUnit.entries.forEachIndexed { index, unit ->
                SegmentedButton(
                    selected = settings.frequencyUnit == unit,
                    // The value is clamped to the new unit's range when it's saved.
                    onClick = { onUpdate { it.copy(frequencyUnit = unit) } },
                    shape = SegmentedButtonDefaults.itemShape(index, FrequencyUnit.entries.size),
                ) {
                    Text(unit.title)
                }
            }
        }
        if (activeCycle != null && activeCycle.phase(Instant.now()) == CyclePhase.CheckInDue) {
            WarningRow(
                "With this schedule your current cycle is already over, so your check-in is due now. " +
                    "Complete it from the Today tab, or pick a longer schedule.",
            )
        }
        if (settings.frequency.isLongerThanAMonth) {
            WarningRow(
                "Check-ins more than a month apart won't be very effective. " +
                    "Consider a shorter cycle so you can adjust your focus more often.",
            )
        }
    }

    val resetFooter = buildString {
        append("The reset is the start of each cycle: stop bad habits and break activities entirely, and do your good habits every day.")
        if (cycleDays < 7) {
            append(" Because your cycle is shorter than a week, the reset lasts the whole cycle by default.")
        } else {
            append(" By default it's the first quarter of the cycle.")
        }
    }
    FormSection(title = "Reset period", footer = resetFooter) {
        SwitchRow(
            label = "Custom reset length",
            checked = settings.customResetDays != null,
            onCheckedChange = { on -> onUpdate { it.copy(customResetDays = if (on) effectiveReset else null) } },
        )
        if (settings.customResetDays != null) {
            StepperRow(
                label = "Reset lasts",
                valueText = dayText(effectiveReset),
                canDecrease = effectiveReset > 1,
                canIncrease = effectiveReset < cycleDays,
                onStep = { delta ->
                    onUpdate {
                        val current = Schedule.resetDays(cycleDays, it.customResetDays)
                        it.copy(customResetDays = (current + delta).coerceIn(1, cycleDays))
                    }
                },
            )
        } else {
            LabeledRow("Reset lasts", dayText(defaultReset))
        }
    }
}

/** Notification and daily check-in controls. Shared by onboarding and Settings. */
@Composable
fun NotificationSettingsSections(settings: AppSettings, onUpdate: SettingsUpdater) {
    val context = LocalContext.current
    val requestPermission = rememberNotificationPermissionRequester()
    var showTimePicker by rememberSaveable { mutableStateOf(false) }

    val allowed = rememberNotificationsAllowed()
    val wantsNotifications = settings.checkInRemindersEnabled ||
        (settings.dailyCheckInEnabled && settings.dailyReminderEnabled)

    FormSection(
        title = "Notifications",
        footer = "Get a notification on the morning your check-in is due and when your reset period ends.",
    ) {
        if (wantsNotifications && !allowed) {
            WarningRow("Notifications are turned off for BetterYou, so you won't get these reminders.")
            TextButton(onClick = { openNotificationSettings(context) }) { Text("Turn on in system settings") }
        }
        SwitchRow(
            label = "Check-in reminders",
            checked = settings.checkInRemindersEnabled,
            onCheckedChange = { on ->
                if (on) requestPermission()
                onUpdate { it.copy(checkInRemindersEnabled = on) }
            },
        )
    }

    FormSection(
        title = "Daily check-in",
        footer = "An optional quick log of how you feel each day, with an optional push notification.",
    ) {
        SwitchRow(
            label = "Daily mood check-in",
            checked = settings.dailyCheckInEnabled,
            onCheckedChange = { on ->
                if (on && settings.dailyReminderEnabled) requestPermission()
                onUpdate { it.copy(dailyCheckInEnabled = on) }
            },
        )
        if (settings.dailyCheckInEnabled) {
            SwitchRow(
                label = "Daily reminder",
                checked = settings.dailyReminderEnabled,
                onCheckedChange = { on ->
                    if (on) requestPermission()
                    onUpdate { it.copy(dailyReminderEnabled = on) }
                },
            )
            if (settings.dailyReminderEnabled) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Remind me at", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = { showTimePicker = true }) {
                        Text(formatTime(settings.dailyReminderHour, settings.dailyReminderMinute))
                    }
                }
            }
        }
    }

    if (showTimePicker) {
        TimePickerDialog(
            hour = settings.dailyReminderHour,
            minute = settings.dailyReminderMinute,
            onConfirm = { hour, minute ->
                onUpdate { it.copy(dailyReminderHour = hour, dailyReminderMinute = minute) }
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false },
        )
    }
}

fun formatTime(hour: Int, minute: Int): String =
    LocalTime.of(hour, minute).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

@Composable
private fun TimePickerDialog(hour: Int, minute: Int, onConfirm: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val state = rememberTimePickerState(
        initialHour = hour,
        initialMinute = minute,
        is24Hour = DateFormat.is24HourFormat(context),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remind me at") },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                TimePicker(state = state)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun WarningRow(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = Orange)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Orange)
    }
}

/** Whether notifications can be shown, re-checked whenever the app comes back to the foreground. */
@Composable
fun rememberNotificationsAllowed(): Boolean {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(Reminders.notificationsAllowed(context)) }
    LifecycleResumeEffect(context) {
        allowed = Reminders.notificationsAllowed(context)
        onPauseOrDispose { }
    }
    return allowed
}

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
