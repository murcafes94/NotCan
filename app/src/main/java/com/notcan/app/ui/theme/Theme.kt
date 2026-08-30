package com.notcan.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val NotCanDarkScheme = darkColorScheme(
    primary = NotCanBlue,
    onPrimary = NotCanOffWhite,
    primaryContainer = NotCanBlue.copy(alpha = 0.20f),
    onPrimaryContainer = NotCanOffWhite,
    secondary = NotCanPurple,
    onSecondary = NotCanOffWhite,
    secondaryContainer = NotCanPurple.copy(alpha = 0.18f),
    tertiary = NotCanGreen,
    onTertiary = NotCanBlack,
    background = NotCanBlack,
    onBackground = NotCanOffWhite,
    surface = NotCanGraphite,
    onSurface = NotCanOffWhite,
    surfaceVariant = NotCanSurface,
    onSurfaceVariant = NotCanGray,
    outline = NotCanBorder,
    error = NotCanRed,
    onError = NotCanOffWhite
)

private val NotCanShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

private val NotCanTypography = Typography(
    headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 25.sp, lineHeight = 31.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium)
)

@Composable
fun NotCanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NotCanDarkScheme,
        shapes = NotCanShapes,
        typography = NotCanTypography,
        content = content
    )
}
