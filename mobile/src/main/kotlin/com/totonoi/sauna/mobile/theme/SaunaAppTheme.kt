package com.totonoi.sauna.mobile.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color

private val Forest = Color(0xFF123C35)
private val Orange = Color(0xFFF08A3C)
private val Cream = Color(0xFFF8F4E8)
private val Sage = Color(0xFFD6E7D8)
private val Charcoal = Color(0xFF17211F)

private val LightColors = lightColorScheme(
    primary = Forest,
    onPrimary = Cream,
    primaryContainer = Sage,
    onPrimaryContainer = Forest,
    secondary = Orange,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDEC4),
    onSecondaryContainer = Color(0xFF351200),
    background = Cream,
    onBackground = Charcoal,
    surface = Color.White,
    onSurface = Charcoal,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DD4B2),
    onPrimary = Color(0xFF003829),
    primaryContainer = Forest,
    onPrimaryContainer = Color(0xFFB9F2CE),
    secondary = Color(0xFFFFB77D),
    onSecondary = Color(0xFF4F2500),
    background = Color(0xFF101816),
    onBackground = Cream,
    surface = Color(0xFF17211F),
    onSurface = Cream,
)

@Composable
fun SaunaAppTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val useDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (useDark) DarkColors else LightColors,
        content = content,
    )
}
