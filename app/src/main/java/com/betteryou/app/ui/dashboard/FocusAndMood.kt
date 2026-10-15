@file:OptIn(ExperimentalMaterial3Api::class)

package com.betteryou.app.ui.dashboard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.betteryou.app.domain.Cycle
import com.betteryou.app.domain.ItemKind
import com.betteryou.app.domain.MoodEntry
import com.betteryou.app.domain.TrackedItem
import com.betteryou.app.ui.components.AllFocusPickers
import com.betteryou.app.ui.components.MoodPicker
import com.betteryou.app.ui.components.scrollingScreen

/** Mid-cycle focus editor opened from the Today tab. */
@Composable
fun FocusEditorScreen(
    cycle: Cycle,
    items: List<TrackedItem>,
    onAddItem: (String, ItemKind) -> String,
    onSave: (Set<String>) -> Unit,
    onCancel: () -> Unit,
) {
    var selection by rememberSaveable(cycle.id) { mutableStateOf(cycle.focusItemIds) }
    BackHandler(onBack = onCancel)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Edit Focus") },
                navigationIcon = {
                    IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, contentDescription = "Cancel") }
                },
                actions = { TextButton(onClick = { onSave(selection) }) { Text("Save") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(Modifier.scrollingScreen(padding)) {
            AllFocusPickers(items, selection, onSelectionChange = { selection = it }, onAddItem = onAddItem)
        }
    }
}

/** Quick daily mood log. */
@Composable
fun MoodEntrySheet(existing: MoodEntry?, onSave: (Int, String) -> Unit, onDismiss: () -> Unit) {
    var mood by rememberSaveable { mutableIntStateOf(existing?.mood ?: 3) }
    var note by rememberSaveable { mutableStateOf(existing?.note ?: "") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Daily Check-in", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("How are you feeling today?", style = MaterialTheme.typography.titleSmall)
            MoodPicker(value = mood, onValueChange = { mood = it })
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Notes (optional)") },
                placeholder = { Text("What's on your mind?") },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(onClick = { onSave(mood, note) }, modifier = Modifier.weight(1f)) { Text("Save") }
            }
        }
    }
}
