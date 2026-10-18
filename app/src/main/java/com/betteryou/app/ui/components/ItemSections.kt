package com.betteryou.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.betteryou.app.domain.ItemKind
import com.betteryou.app.domain.TrackedItem
import com.betteryou.app.ui.AddResult

/** Text field with an add button. [onAdd] returns true when the text was used and the field can be cleared. */
@Composable
fun AddItemField(
    placeholder: String,
    onAdd: (String) -> Boolean,
    modifier: Modifier = Modifier,
    message: String? = null,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val trimmed = text.trim()
    val submit: () -> Unit = {
        if (trimmed.isNotEmpty() && onAdd(trimmed)) text = ""
    }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        placeholder = { Text(placeholder) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        trailingIcon = {
            IconButton(onClick = submit, enabled = trimmed.isNotEmpty()) {
                Icon(Icons.Filled.AddCircle, contentDescription = "Add")
            }
        },
        supportingText = if (message != null) {
            { Text(message) }
        } else {
            null
        },
        modifier = modifier.fillMaxWidth(),
    )
}

/** Section for adding, renaming and deleting items of one kind. */
@Composable
fun ItemEditorSection(
    kind: ItemKind,
    items: List<TrackedItem>,
    onAdd: (String) -> AddResult,
    onRename: (TrackedItem, String) -> Unit,
    onDelete: (TrackedItem) -> Unit,
    nameConflict: (TrackedItem, String) -> String?,
    showsSuggestions: Boolean = false,
) {
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val add: (String) -> Boolean = { name ->
        when (val result = onAdd(name)) {
            is AddResult.Added -> {
                message = null
                true
            }
            is AddResult.AlreadyExists -> {
                message = "“${result.name}” is already in this list."
                false
            }
        }
    }

    FormSection(title = kind.title, footer = kind.explanation, icon = kind.icon, tint = kind.tint) {
        items.forEach { item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { editingId = item.id }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(item.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = "Edit ${item.name}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        AddItemField(kind.placeholder, add, message = message)
    }

    if (showsSuggestions) {
        val available = kind.suggestions.filter { suggestion -> items.none { it.name.equals(suggestion, ignoreCase = true) } }
        if (available.isNotEmpty()) {
            FormSection(title = "Suggestions") {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    available.forEach { suggestion ->
                        AssistChip(
                            onClick = { add(suggestion) },
                            label = { Text(suggestion) },
                            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = kind.tint.copy(alpha = 0.15f),
                                labelColor = kind.tint,
                                leadingIconContentColor = kind.tint,
                            ),
                            border = null,
                        )
                    }
                }
            }
        }
    }

    val editing = items.firstOrNull { it.id == editingId }
    if (editing != null) {
        EditItemDialog(
            item = editing,
            nameConflict = { nameConflict(editing, it) },
            onSave = { onRename(editing, it); editingId = null },
            onDelete = { onDelete(editing); editingId = null },
            onDismiss = { editingId = null },
        )
    }
}

@Composable
private fun EditItemDialog(
    item: TrackedItem,
    nameConflict: (String) -> String?,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable(item.id) { mutableStateOf(item.name) }
    val conflict = nameConflict(name)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                isError = conflict != null,
                supportingText = if (conflict != null) {
                    { Text("“$conflict” is already in this list.") }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank() && conflict == null) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/** Section for choosing which items of a kind to focus on (up to the kind's limit). */
@Composable
fun FocusPickerSection(
    kind: ItemKind,
    items: List<TrackedItem>,
    selection: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    onAddItem: (String) -> AddResult,
) {
    val selectedCount = items.count { it.id in selection }
    val limit = kind.focusLimit
    val atLimit = limit != null && selectedCount >= limit
    var message by remember { mutableStateOf<String?>(null) }
    val limitHint = "You can focus on up to $limit ${kind.title.lowercase()}. Unselect one first."

    FormSection(
        title = kind.title,
        icon = kind.icon,
        tint = kind.tint,
        footer = if (limit != null) "Pick up to $limit. ${kind.goal(inReset = true)}" else kind.goal(inReset = true),
        trailingHeader = {
            Text(
                if (limit != null) "$selectedCount/$limit" else "$selectedCount selected",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    ) {
        if (items.isEmpty()) {
            Text("Nothing here yet — add one below.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items.forEach { item ->
            val selected = item.id in selection
            val available = selected || !atLimit
            Row(
                Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = selected,
                        role = Role.Checkbox,
                        onValueChange = { on ->
                            when {
                                !on -> {
                                    message = null
                                    onSelectionChange(selection - item.id)
                                }
                                atLimit -> message = limitHint
                                else -> {
                                    message = null
                                    onSelectionChange(selection + item.id)
                                }
                            }
                        },
                    )
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    item.name,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (available) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
                Icon(
                    if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = when {
                        selected -> kind.tint
                        available -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    },
                )
            }
        }
        AddItemField(
            kind.placeholder,
            message = message,
            onAdd = { name ->
                val result = onAddItem(name)
                val alreadySelected = result.id in selection
                message = when {
                    alreadySelected -> "“$name” is already selected."
                    atLimit && result is AddResult.Added ->
                        "Added “$name” to your list. You're already focusing on $limit — unselect one to focus on it."
                    atLimit -> "“$name” is already in your list. $limitHint"
                    result is AddResult.AlreadyExists -> "“${result.name}” was already in your list, so it's now selected."
                    else -> null
                }
                if (!atLimit && !alreadySelected) onSelectionChange(selection + result.id)
                true
            },
        )
    }
}

/** Focus pickers for every kind, as used in onboarding, the focus editor and the check-in. */
@Composable
fun AllFocusPickers(
    items: List<TrackedItem>,
    selection: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    onAddItem: (String, ItemKind) -> AddResult,
) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        ItemKind.entries.forEach { kind ->
            FocusPickerSection(
                kind = kind,
                items = items.filter { it.kind == kind },
                selection = selection,
                onSelectionChange = onSelectionChange,
                onAddItem = { onAddItem(it, kind) },
            )
        }
    }
}
