package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = CyberCyan,
    onPrimary = OnCyberCyan,
    primaryContainer = Color(0xFF0A3347),
    onPrimaryContainer = CyberCyan,
    secondary = EmeraldPulse,
    onSecondary = Color(0xFF022C22),
    secondaryContainer = EmeraldSurface,
    onSecondaryContainer = EmeraldPulse,
    tertiary = AmberWarning,
    onTertiary = Color(0xFF2B1300),
    tertiaryContainer = AmberSurface,
    onTertiaryContainer = AmberWarning,
    error = CoralAlert,
    onError = Color.White,
    errorContainer = CoralSurface,
    onErrorContainer = CoralAlert,
    background = ObsidianCanvas,
    onBackground = TextPrimaryDark,
    surface = DeepSlateSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = ElevatedCardSurface,
    onSurfaceVariant = TextSecondaryDark,
    outline = SubtleOutline,
    outlineVariant = Color(0xFF1D2940)
)

private val LightColorScheme = lightColorScheme(
    primary = CyanPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0C4A6E),
    secondary = EmeraldLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1FAE5),
    onSecondaryContainer = Color(0xFF065F46),
    tertiary = AmberWarning,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF78350F),
    error = CoralAlert,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    background = LightCanvas,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightCardSurface,
    onSurfaceVariant = TextSecondaryLight,
    outline = LightOutline,
    outlineVariant = Color(0xFFE2E8F0)
)

val SysPulseShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun SysPulseTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SysPulseTypography,
        shapes = SysPulseShapes,
        content = content
    )
}
