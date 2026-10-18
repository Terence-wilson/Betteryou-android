package com.betteryou.app.ui.lists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.betteryou.app.domain.ItemKind
import com.betteryou.app.domain.TrackedItem
import com.betteryou.app.ui.AddResult
import com.betteryou.app.ui.components.scrollingScreen
import com.betteryou.app.ui.components.ItemEditorSection

@Composable
fun ListsScreen(
    items: List<TrackedItem>,
    onAdd: (String, ItemKind) -> AddResult,
    onRename: (TrackedItem, String) -> Unit,
    onDelete: (TrackedItem) -> Unit,
    nameConflict: (TrackedItem, String) -> String?,
    contentPadding: PaddingValues,
) {
    Column(
        Modifier.scrollingScreen(contentPadding),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            "Tap an item to rename or delete it. Deleting keeps its past ratings in Progress.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        ItemKind.entries.forEach { kind ->
            ItemEditorSection(
                kind = kind,
                items = items.filter { it.kind == kind },
                onAdd = { onAdd(it, kind) },
                onRename = onRename,
                onDelete = onDelete,
                nameConflict = nameConflict,
            )
        }
    }
}
