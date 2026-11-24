package com.example.ui.theme

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.data.local.AppThemeMode

private val SpacedomDarkColorScheme = darkColorScheme(
    primary = ElectricViolet,
    onPrimary = StarlightText,
    primaryContainer = ElectricVioletContainer,
    onPrimaryContainer = VioletGlow,
    secondary = CosmicCyan,
    onSecondary = ObsidianBg,
    secondaryContainer = CosmicCyanContainer,
    onSecondaryContainer = CosmicCyan,
    tertiary = AmberGold,
    onTertiary = ObsidianBg,
    background = ObsidianBg,
    onBackground = StarlightText,
    surface = ObsidianSurface,
    onSurface = StarlightText,
    surfaceVariant = ObsidianSurfaceElevated,
    onSurfaceVariant = MutedSlate,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianSurfaceElevated,
    surfaceContainerHigh = ObsidianCard,
    surfaceContainerHighest = ObsidianCardHover,
    outline = OutlineSubtle,
    outlineVariant = OutlineSubtle,
    error = RoseAlert,
    onError = StarlightText,
    errorContainer = RoseAlertContainer,
    onErrorContainer = RoseAlert
)

private val SpacedomLightColorScheme = lightColorScheme(
    primary = DaylightPrimary,
    onPrimary = Color.White,
    primaryContainer = DaylightPrimaryContainer,
    onPrimaryContainer = DaylightPrimary,
    secondary = DaylightSecondary,
    onSecondary = Color.White,
    secondaryContainer = DaylightSecondaryContainer,
    onSecondaryContainer = DaylightSecondary,
    tertiary = AmberGold,
    onTertiary = Color.White,
    background = DaylightBg,
    onBackground = DaylightInk,
    surface = DaylightSurface,
    onSurface = DaylightInk,
    surfaceVariant = DaylightSurfaceElevated,
    onSurfaceVariant = DaylightMutedInk,
    surfaceContainerLowest = DaylightSurface,
    surfaceContainerLow = DaylightBg,
    surfaceContainer = DaylightSurfaceElevated,
    surfaceContainerHigh = DaylightCard,
    surfaceContainerHighest = DaylightSurfaceElevated,
    outline = DaylightOutline,
    outlineVariant = DaylightOutline,
    error = DaylightError,
    onError = Color.White,
    errorContainer = DaylightErrorContainer,
    onErrorContainer = DaylightError
)

val SpacedomShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
private fun animateThemeColor(target: Color): Color {
    val color by animateColorAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 280),
        label = "theme_color"
    )
    return color
}

@Composable
private fun animatedColorScheme(target: ColorScheme): ColorScheme {
    return target.copy(
        primary = animateThemeColor(target.primary),
        secondary = animateThemeColor(target.secondary),
        background = animateThemeColor(target.background),
        onBackground = animateThemeColor(target.onBackground),
        surface = animateThemeColor(target.surface),
        onSurface = animateThemeColor(target.onSurface),
        surfaceVariant = animateThemeColor(target.surfaceVariant),
        onSurfaceVariant = animateThemeColor(target.onSurfaceVariant),
        surfaceContainerLowest = animateThemeColor(target.surfaceContainerLowest),
        surfaceContainerLow = animateThemeColor(target.surfaceContainerLow),
        surfaceContainer = animateThemeColor(target.surfaceContainer),
        surfaceContainerHigh = animateThemeColor(target.surfaceContainerHigh),
        surfaceContainerHighest = animateThemeColor(target.surfaceContainerHighest),
        outline = animateThemeColor(target.outline),
        outlineVariant = animateThemeColor(target.outlineVariant)
    )
}

@Composable
fun SpacedomTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val targetScheme = if (isDark) SpacedomDarkColorScheme else SpacedomLightColorScheme
    val colorScheme = animatedColorScheme(targetScheme)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !isDark
                    isAppearanceLightNavigationBars = !isDark
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SpacedomTypography,
        shapes = SpacedomShapes,
        content = content
    )
}
