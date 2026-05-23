package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = GoldNeon,
    onPrimary = ObsidianDark,
    secondary = MutedGold,
    background = ObsidianDark,
    surface = SurfaceDark,
    onBackground = TextLight,
    onSurface = TextLight
)

private val LightColorScheme = lightColorScheme(
    primary = GoldPrimary,
    onPrimary = WhiteBase,
    secondary = GoldSecondary,
    background = WhiteBase,
    surface = SurfaceLight,
    onBackground = TextDark,
    onSurface = TextDark,
    secondaryContainer = GoldHighlight
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false, // Configured via viewModel state
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
