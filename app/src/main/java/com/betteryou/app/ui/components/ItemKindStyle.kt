package com.betteryou.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Verified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.betteryou.app.domain.ItemKind
import com.betteryou.app.ui.theme.Blue
import com.betteryou.app.ui.theme.Green
import com.betteryou.app.ui.theme.Purple
import com.betteryou.app.ui.theme.Red

val ItemKind.icon: ImageVector
    get() = when (this) {
        ItemKind.HOBBY -> Icons.Filled.Palette
        ItemKind.GOOD_HABIT -> Icons.Filled.Verified
        ItemKind.BAD_HABIT -> Icons.Filled.Block
        ItemKind.BREAK_ACTIVITY -> Icons.Filled.PauseCircle
    }

val ItemKind.tint: Color
    get() = when (this) {
        ItemKind.HOBBY -> Purple
        ItemKind.GOOD_HABIT -> Green
        ItemKind.BAD_HABIT -> Red
        ItemKind.BREAK_ACTIVITY -> Blue
    }
