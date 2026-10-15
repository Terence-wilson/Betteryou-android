package com.betteryou.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BrandGreen = Color(0xFF2EA67A)
val Purple = Color(0xFF8E5BD8)
val Green = Color(0xFF2E9E5B)
val Red = Color(0xFFE0453A)
val Blue = Color(0xFF2F7BEA)
val Orange = Color(0xFFF08A24)
val Pink = Color(0xFFE5487D)

private val LightColors = lightColorScheme(
    primary = Color(0xFF16805A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB4F1D3),
    onPrimaryContainer = Color(0xFF002115),
    secondary = Color(0xFF4C6358),
    secondaryContainer = Color(0xFFCEE9DA),
    onSecondaryContainer = Color(0xFF092016),
    tertiary = Color(0xFF3E6374),
    background = Color(0xFFF2F4F3),
    onBackground = Color(0xFF191C1A),
    surface = Color.White,
    onSurface = Color(0xFF191C1A),
    surfaceVariant = Color(0xFFE6ECE8),
    onSurfaceVariant = Color(0xFF5A635E),
    outlineVariant = Color(0xFFD5DCD8),
    surfaceContainer = Color(0xFFF2F4F3),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5FD3A4),
    onPrimary = Color(0xFF003825),
    primaryContainer = Color(0xFF005138),
    onPrimaryContainer = Color(0xFFB4F1D3),
    secondary = Color(0xFFB3CCBF),
    secondaryContainer = Color(0xFF354B41),
    onSecondaryContainer = Color(0xFFCEE9DA),
    tertiary = Color(0xFFA6CCDF),
    background = Color(0xFF0F1412),
    onBackground = Color(0xFFE1E3E0),
    surface = Color(0xFF1B211E),
    onSurface = Color(0xFFE1E3E0),
    surfaceVariant = Color(0xFF2A322E),
    onSurfaceVariant = Color(0xFFA9B2AD),
    outlineVariant = Color(0xFF3A433F),
    surfaceContainer = Color(0xFF1B211E),
)

@Composable
fun BetterYouTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
