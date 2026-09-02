package com.notcan.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val NotCanDarkScheme = darkColorScheme(
    primary = NotCanBlue,
    onPrimary = Color.White,
    primaryContainer = NotCanBlue.copy(alpha = 0.20f),
    onPrimaryContainer = Color.White,
    secondary = NotCanPurple,
    onSecondary = Color.White,
    secondaryContainer = NotCanPurple.copy(alpha = 0.18f),
    tertiary = NotCanGreen,
    onTertiary = Color(0xFF08110C),
    background = Color(0xFF080D15),
    onBackground = Color(0xFFF4F7FB),
    surface = Color(0xFF0D1420),
    onSurface = Color(0xFFF4F7FB),
    surfaceVariant = Color(0xFF131D2A),
    onSurfaceVariant = Color(0xFFA7B2C3),
    outline = Color(0xFF263548),
    error = NotCanRed,
    onError = Color.White
)

private val NotCanLightScheme = lightColorScheme(
    primary = NotCanBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8FF),
    onPrimaryContainer = Color(0xFF17345F),
    secondary = NotCanBrownAccent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9DDD0),
    onSecondaryContainer = Color(0xFF45301F),
    tertiary = NotCanGreen,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDCEFE4),
    onTertiaryContainer = Color(0xFF173B28),
    background = Color(0xFFFFFDF9),
    onBackground = Color(0xFF1F252C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F252C),
    surfaceVariant = Color(0xFFF5F2EE),
    onSurfaceVariant = Color(0xFF626B76),
    outline = Color(0xFFD8D3CC),
    error = NotCanRed,
    onError = Color.White
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
fun NotCanTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    applyNotCanPalette(darkTheme)
    MaterialTheme(
        colorScheme = if (darkTheme) NotCanDarkScheme else NotCanLightScheme,
        shapes = NotCanShapes,
        typography = NotCanTypography,
        content = content
    )
}
