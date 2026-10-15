package com.betteryou.app.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Scrollable screen body inside a Scaffold that stays clear of the bars and the keyboard. */
@Composable
fun Modifier.scrollingScreen(contentPadding: PaddingValues): Modifier = this
    .fillMaxSize()
    .padding(contentPadding)
    .consumeWindowInsets(contentPadding)
    .imePadding()
    .verticalScroll(rememberScrollState())
    .padding(16.dp)
