package com.apia.musicplayer.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Dark music player palette
val DeepPurple = Color(0xFF1A0533)
val Purple = Color(0xFF6B21A8)
val PurpleLight = Color(0xFFA855F7)
val PurpleSurface = Color(0xFF2D1B4E)
val OnSurface = Color(0xFFE8D5FF)
val OnSurfaceVariant = Color(0xFFB39DCC)
val Accent = Color(0xFFE040FB)

private val DarkColorScheme = darkColorScheme(
    primary = PurpleLight,
    onPrimary = Color.White,
    primaryContainer = Purple,
    onPrimaryContainer = Color(0xFFF3E8FF),
    secondary = Accent,
    background = DeepPurple,
    onBackground = OnSurface,
    surface = PurpleSurface,
    onSurface = OnSurface,
    onSurfaceVariant = OnSurfaceVariant,
    surfaceVariant = Color(0xFF3D2660),
    outline = Color(0xFF7B5EA7),
)

@Composable
fun MusicPlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography(),
        content = content
    )
}