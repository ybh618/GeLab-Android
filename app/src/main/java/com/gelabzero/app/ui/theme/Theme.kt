package com.gelabzero.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = AccentDark,
    onPrimaryContainer = Color.White,
    background = Cloud,
    onBackground = Slate,
    surface = Color.White,
    onSurface = Slate,
    error = ErrorRed,
)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = AccentDark,
    onPrimaryContainer = Color.White,
    background = Slate,
    onBackground = Cloud,
    surface = Color(0xFF2B2D36),
    onSurface = Cloud,
    error = ErrorRed,
)

@Composable
fun GelabTheme(
    content: @Composable () -> Unit,
) {
    val colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
