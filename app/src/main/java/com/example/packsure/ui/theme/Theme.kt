package com.example.packsure.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF16251F)
val Forest = Color(0xFF164E3A)
val Mint = Color(0xFFB8F2D4)
val Leaf = Color(0xFF27855E)
val Canvas = Color(0xFFF6F7F3)
val Sand = Color(0xFFEAF0EA)
val Amber = Color(0xFFB26A00)
val Alert = Color(0xFFB93636)

private val Light = lightColorScheme(
    primary = Forest, onPrimary = Color.White, primaryContainer = Mint,
    secondary = Leaf, background = Canvas, surface = Color.White,
    onBackground = Ink, onSurface = Ink, error = Alert
)
private val Dark = darkColorScheme(
    primary = Mint, onPrimary = Color(0xFF073724), secondary = Color(0xFF87D9AF),
    background = Color(0xFF101814), surface = Color(0xFF18231E),
    onBackground = Color(0xFFE2ECE4), onSurface = Color(0xFFE2ECE4), error = Color(0xFFFFB4AB)
)

@Composable fun PackSureTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content
)
