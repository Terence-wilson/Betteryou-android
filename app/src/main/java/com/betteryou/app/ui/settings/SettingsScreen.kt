package com.betteryou.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.betteryou.app.domain.AppSettings
import com.betteryou.app.domain.Cycle
import com.betteryou.app.ui.components.scrollingScreen
import com.betteryou.app.ui.components.FormSection
import com.betteryou.app.ui.components.LabeledRow
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val mediumDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
private val mediumDateShortTime = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

@Composable
fun SettingsScreen(
    settings: AppSettings,
    activeCycle: Cycle?,
    onUpdate: SettingsUpdater,
    contentPadding: PaddingValues,
) {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "1.0"
    }
    val zone = ZoneId.systemDefault()

    Column(
        Modifier.scrollingScreen(contentPadding),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        ScheduleSettingsSections(settings, onUpdate)

        if (activeCycle != null) {
            FormSection(
                title = "Current cycle",
                footer = "Changes to frequency and reset length apply to your current cycle immediately.",
            ) {
                LabeledRow("Started", mediumDate.format(activeCycle.startDate.atZone(zone)))
                LabeledRow("Reset ends", mediumDate.format(activeCycle.resetEndDate.atZone(zone)))
                LabeledRow("Check-in due", mediumDateShortTime.format(activeCycle.checkInDate.atZone(zone)))
            }
        }

        NotificationSettingsSections(settings, onUpdate)

        FormSection(title = "About") {
            LabeledRow("Version", version)
        }
    }
}
