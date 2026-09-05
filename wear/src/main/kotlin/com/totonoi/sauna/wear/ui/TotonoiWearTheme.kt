package com.totonoi.sauna.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

private val SaunaColors = Colors(
    primary = Color(0xFF9DD4B2),
    primaryVariant = Color(0xFF123C35),
    secondary = Color(0xFFFFB77D),
    secondaryVariant = Color(0xFFF08A3C),
    background = Color(0xFF101816),
    surface = Color(0xFF17211F),
    error = Color(0xFFFFB4AB),
    onPrimary = Color(0xFF003829),
    onSecondary = Color(0xFF4F2500),
    onBackground = Color(0xFFF8F4E8),
    onSurface = Color(0xFFF8F4E8),
    onError = Color(0xFF690005),
)

@Composable
fun TotonoiWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(colors = SaunaColors, content = content)
}
