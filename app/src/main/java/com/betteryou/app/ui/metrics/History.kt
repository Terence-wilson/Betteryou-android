@file:OptIn(ExperimentalMaterial3Api::class)

package com.betteryou.app.ui.metrics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.betteryou.app.domain.CheckIn
import com.betteryou.app.domain.ItemKind
import com.betteryou.app.domain.ItemRating
import com.betteryou.app.domain.MoodEntry
import com.betteryou.app.domain.MoodScale
import com.betteryou.app.ui.components.AppCard
import com.betteryou.app.ui.components.ConfirmDialog
import com.betteryou.app.ui.components.icon
import com.betteryou.app.ui.components.tint
import com.betteryou.app.ui.dashboard.MoodEntrySheet
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val longDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
private val dayFormat = DateTimeFormatter.ofPattern("EEE, MMM d")

/** Past check-ins, newest first, each opening its ratings and reflection. */
@Composable
fun CheckInHistoryCard(
    checkIns: List<CheckIn>,
    onUpdateNote: (CheckIn, String) -> Unit,
    onDelete: (CheckIn) -> Unit,
) {
    if (checkIns.isEmpty()) return
    var openId by rememberSaveable { mutableStateOf<String?>(null) }

    AppCard {
        Text("Past check-ins", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        checkIns.asReversed().forEachIndexed { index, checkIn ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { openId = checkIn.id }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(longDate.format(checkIn.date.atZone(ZoneId.systemDefault())), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        checkIn.note.ifBlank { "${checkIn.ratings.size} ${if (checkIn.ratings.size == 1) "rating" else "ratings"}" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    val open = checkIns.firstOrNull { it.id == openId }
    if (open != null) {
        CheckInDetailSheet(
            checkIn = open,
            onSaveNote = {
                onUpdateNote(open, it)
                openId = null
            },
            onDelete = {
                onDelete(open)
                openId = null
            },
            onDismiss = { openId = null },
        )
    }
}

@Composable
private fun CheckInDetailSheet(
    checkIn: CheckIn,
    onSaveNote: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var note by rememberSaveable(checkIn.id) { mutableStateOf(checkIn.note) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Check-in · ${longDate.format(checkIn.date.atZone(ZoneId.systemDefault()))}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            if (checkIn.ratings.isEmpty()) {
                Text("No ratings were given.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            checkIn.ratings.forEach { RatingSummary(it) }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Reflection") },
                minLines = 3,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Close") }
                Button(onClick = { onSaveNote(note) }, enabled = note != checkIn.note, modifier = Modifier.weight(1f)) {
                    Text("Save note")
                }
            }
            TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Delete this check-in", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete this check-in?",
            message = "Its ratings and reflection will be removed from your progress. This can't be undone.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun RatingSummary(rating: ItemRating) {
    val parts = if (rating.kind == ItemKind.HOBBY) {
        listOfNotNull(
            rating.consistency?.let { "Consistency $it" },
            rating.enjoyment?.let { "Enjoyment $it" },
            rating.improvement?.let { "Improvement $it" },
        )
    } else {
        listOfNotNull(
            rating.adherence?.let { "Rated $it/5" },
            rating.resetDaysTotal?.let { "reset kept ${rating.resetDaysKept ?: 0}/$it days" },
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Icon(rating.kind.icon, contentDescription = null, tint = rating.kind.tint)
        Column {
            Text(rating.itemName, style = MaterialTheme.typography.bodyLarge)
            Text(
                parts.joinToString(" · ").ifEmpty { "Not rated" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Every mood entry, newest first; tap one to edit or delete it. */
@Composable
fun MoodEntriesCard(
    moods: List<MoodEntry>,
    onSave: (LocalDate, Int, String) -> Unit,
    onDelete: (LocalDate) -> Unit,
) {
    var openDay by rememberSaveable { mutableStateOf<Long?>(null) }

    AppCard {
        Text("Entries", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Tap an entry to edit or delete it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        moods.asReversed().forEachIndexed { index, entry ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { openDay = entry.day.toEpochDay() }
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(MoodScale.emoji(entry.mood))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "${dayFormat.format(entry.day)} · ${MoodScale.label(entry.mood)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (entry.note.isNotBlank()) Text(entry.note, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }

    val open = moods.firstOrNull { it.day.toEpochDay() == openDay }
    if (open != null) {
        MoodEntrySheet(
            existing = open,
            day = open.day,
            onSave = { mood, note ->
                onSave(open.day, mood, note)
                openDay = null
            },
            onDelete = {
                onDelete(open.day)
                openDay = null
            },
            onDismiss = { openDay = null },
        )
    }
}
