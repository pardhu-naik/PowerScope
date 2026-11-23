package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class PowerScopeExtendedColors(
    val emerald: Color,
    val cyan: Color,
    val amber: Color,
    val coral: Color,
    val cardBorder: Color
)

val LocalPowerScopeColors = staticCompositionLocalOf {
    PowerScopeExtendedColors(
        emerald = EmeraldPrimary,
        cyan = CyanAccent,
        amber = AmberWarning,
        coral = CoralDanger,
        cardBorder = DarkOutline
    )
}

private val DarkColors = darkColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color(0xFF00241A),
    primaryContainer = Color(0xFF003D2D),
    onPrimaryContainer = Color(0xFF6EF7CE),
    secondary = CyanAccent,
    onSecondary = Color(0xFF00222A),
    secondaryContainer = Color(0xFF003B4A),
    onSecondaryContainer = Color(0xFF8BE8FF),
    tertiary = AmberWarning,
    onTertiary = Color(0xFF2B1D00),
    error = CoralDanger,
    onError = Color(0xFF3E0013),
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline
)

private val LightColors = lightColorScheme(
    primary = EmeraldDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD2F8EC),
    onPrimaryContainer = Color(0xFF003828),
    secondary = CyanDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD5F3FA),
    onSecondaryContainer = Color(0xFF003442),
    tertiary = AmberDark,
    onTertiary = Color.White,
    error = CoralDanger,
    onError = Color.White,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline
)

@Composable
fun PowerScopeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val extendedColors = if (darkTheme) {
        PowerScopeExtendedColors(
            emerald = EmeraldPrimary,
            cyan = CyanAccent,
            amber = AmberWarning,
            coral = CoralDanger,
            cardBorder = DarkOutline
        )
    } else {
        PowerScopeExtendedColors(
            emerald = EmeraldDark,
            cyan = CyanDark,
            amber = AmberDark,
            coral = CoralDanger,
            cardBorder = LightOutline
        )
    }

    CompositionLocalProvider(LocalPowerScopeColors provides extendedColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = PowerScopeTypography,
            content = content
        )
    }
}
