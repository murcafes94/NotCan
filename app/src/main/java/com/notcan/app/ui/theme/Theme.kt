package com.notcan.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val NotCanDarkScheme = darkColorScheme(
    primary = NotCanBlue,
    onPrimary = NotCanOffWhite,
    primaryContainer = NotCanBlueContainer,
    onPrimaryContainer = NotCanOffWhite,
    secondary = NotCanPurple,
    onSecondary = NotCanOffWhite,
    secondaryContainer = NotCanPurpleContainer,
    onSecondaryContainer = NotCanOffWhite,
    tertiary = NotCanCyan,
    onTertiary = NotCanBlack,
    background = NotCanBlack,
    onBackground = NotCanOffWhite,
    surface = NotCanGraphite,
    onSurface = NotCanOffWhite,
    surfaceVariant = NotCanSurface,
    onSurfaceVariant = NotCanGray,
    outline = NotCanBorder,
    outlineVariant = NotCanSurfaceHigh,
    error = NotCanRed,
    onError = NotCanOffWhite
)

private val NotCanShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun NotCanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NotCanDarkScheme,
        shapes = NotCanShapes,
        content = content
    )
}
