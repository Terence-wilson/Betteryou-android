package com.betteryou.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.betteryou.app.domain.MoodScale
import com.betteryou.app.ui.theme.Orange
import kotlinx.coroutines.delay

/** Rounded card used throughout the app. */
@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

/** A titled group of rows, like a section in a settings form. */
@Composable
fun FormSection(
    title: String? = null,
    modifier: Modifier = Modifier,
    footer: String? = null,
    icon: ImageVector? = null,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    trailingHeader: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (title != null) {
            Row(
                Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (icon != null) Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                Text(
                    title.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = tint,
                    modifier = Modifier.weight(1f),
                )
                trailingHeader?.invoke()
            }
        }
        AppCard(content = content)
        if (footer != null) {
            Text(
                footer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
fun LabeledRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** A value with − and + buttons. Hold a button to keep stepping. */
@Composable
fun StepperRow(
    label: String,
    valueText: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onStep: (delta: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(valueText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        RepeatingIconButton(Icons.Filled.Remove, "Decrease $label", canDecrease) { onStep(-1) }
        RepeatingIconButton(Icons.Filled.Add, "Increase $label", canIncrease) { onStep(1) }
    }
}

@Composable
private fun RepeatingIconButton(icon: ImageVector, description: String, enabled: Boolean, onStep: () -> Unit) {
    val currentOnStep by rememberUpdatedState(onStep)
    val currentEnabled by rememberUpdatedState(enabled)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    var repeated by remember { mutableStateOf(false) }

    LaunchedEffect(pressed) {
        if (!pressed) return@LaunchedEffect
        delay(400)
        var interval = 150L
        while (currentEnabled) {
            repeated = true
            currentOnStep()
            delay(interval)
            interval = maxOf(40L, interval - 15L)
        }
    }

    FilledTonalIconButton(
        onClick = {
            // A hold already stepped; don't add one more on release.
            if (!repeated) currentOnStep()
            repeated = false
        },
        enabled = enabled,
        interactionSource = interactionSource,
    ) {
        Icon(icon, contentDescription = description)
    }
}

/** A 1–5 rating control with labelled extremes. */
@Composable
fun RatingRow(
    title: String,
    value: Int?,
    onValueChange: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null,
    lowLabel: String = "1",
    highLabel: String = "5",
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (caption != null) {
                    Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (value == null) {
                Text("Not rated", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (score in 1..5) {
                val selected = score == value
                Surface(
                    selected = selected,
                    // Tapping the selected score again clears it.
                    onClick = { onValueChange(if (selected) null else score) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) tint else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .semantics { contentDescription = "$title $score of 5" },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("$score", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text(lowLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(highLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Five emoji buttons for the daily mood check-in. */
@Composable
fun MoodPicker(value: Int, onValueChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (mood in 1..5) {
            val selected = mood == value
            val shape = RoundedCornerShape(12.dp)
            Surface(
                selected = selected,
                onClick = { onValueChange(mood) },
                shape = shape,
                color = if (selected) accent.copy(alpha = 0.18f) else Color.Transparent,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 68.dp)
                    .border(2.dp, if (selected) accent else Color.Transparent, shape)
                    .semantics {
                        contentDescription = MoodScale.label(mood)
                        stateDescription = if (selected) "Selected" else ""
                    },
            ) {
                Column(
                    Modifier.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(MoodScale.emoji(mood), fontSize = 28.sp)
                    Text(MoodScale.label(mood), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/** Progress through a cycle with the reset portion shaded. */
@Composable
fun CycleProgressBar(progress: Double, resetFraction: Double, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .semantics {
                contentDescription = "Cycle progress"
                stateDescription = "${(progress * 100).toInt()} percent"
            },
    ) {
        Box(
            Modifier
                .fillMaxWidth(resetFraction.toFloat())
                .height(10.dp)
                .background(Orange.copy(alpha = 0.35f)),
        )
        Box(
            Modifier
                .fillMaxWidth(progress.toFloat())
                .height(10.dp)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

/** Small round bullet used in lists. */
@Composable
fun Dot(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(6.dp)
            .clip(RoundedCornerShape(50))
            .background(color),
    )
}

@Composable
fun EmptyState(title: String, message: String, icon: ImageVector, modifier: Modifier = Modifier) {
    AppCard(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

fun dayText(days: Int): String = "$days ${if (days == 1) "day" else "days"}"

/** Yes/no question before an action that can't be undone or that loses work. */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "Cancel",
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmLabel,
                    color = if (destructive) MaterialTheme.colorScheme.error else Color.Unspecified,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissLabel) }
        },
    )
}
