package com.selfguard.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B5E20),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA8F0AA),
    onPrimaryContainer = Color(0xFF002204),
    secondary = Color(0xFF52634F),
    background = Color(0xFFFBFDF7),
    surface = Color(0xFFFBFDF7),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8CD68F),
    onPrimary = Color(0xFF00390A),
    primaryContainer = Color(0xFF005220),
    onPrimaryContainer = Color(0xFFA8F0AA),
    secondary = Color(0xFFB9CCB4),
    background = Color(0xFF111410),
    surface = Color(0xFF111410),
    error = Color(0xFFFFB4AB),
)

@Composable
fun SelfGuardTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content,
    )
}
