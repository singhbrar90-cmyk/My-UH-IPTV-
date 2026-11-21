package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val UhDarkColorScheme = darkColorScheme(
    primary = UhCrimson,
    onPrimary = UhWhite,
    primaryContainer = UhCrimsonDark,
    onPrimaryContainer = UhWhite,
    secondary = UhSilver,
    onSecondary = UhBlack,
    secondaryContainer = UhSurfaceHighlight,
    onSecondaryContainer = UhWhite,
    tertiary = UhLiveGreen,
    onTertiary = UhBlack,
    background = UhBlack,
    onBackground = UhWhite,
    surface = UhSurface,
    onSurface = UhWhite,
    surfaceVariant = UhSurfaceElevated,
    onSurfaceVariant = UhSilver,
    outline = UhBorder,
    outlineVariant = UhSurfaceHighlight,
    error = UhErrorRed,
    onError = UhWhite
)

private val UhShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun UhIptvTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = UhDarkColorScheme,
        typography = UhTypography,
        shapes = UhShapes,
        content = content
    )
}
