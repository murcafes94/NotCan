package com.notcan.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val NotCanDarkScheme = darkColorScheme(
    primary = NotCanBlue,
    onPrimary = NotCanOffWhite,
    secondary = NotCanBrown,
    onSecondary = NotCanOffWhite,
    tertiary = NotCanGreen,
    background = NotCanBlack,
    onBackground = NotCanOffWhite,
    surface = NotCanGraphite,
    onSurface = NotCanOffWhite,
    surfaceVariant = NotCanSurface,
    onSurfaceVariant = NotCanGray,
    error = NotCanRed,
    onError = NotCanOffWhite
)

@Composable
fun NotCanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NotCanDarkScheme,
        content = content
    )
}
