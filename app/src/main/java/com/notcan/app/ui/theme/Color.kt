package com.notcan.app.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

// Accent colors remain stable in both themes.
val NotCanBlue = Color(0xFF3478F6)
val NotCanBlueSoft = Color(0xFF6C9CFF)
val NotCanPurple = Color(0xFF9A6BE8)
val NotCanCyan = Color(0xFF49C8E8)
val NotCanGreen = Color(0xFF3B9C6A)
val NotCanAmber = Color(0xFFB9863F)
val NotCanRed = Color(0xFFD55460)
val NotCanBrownAccent = Color(0xFF8B6746)

// Semantic colors are state-backed so legacy screens that still reference these
// names also adapt when the user toggles the theme.
var NotCanBlack by mutableStateOf(Color(0xFF080D15))
    private set
var NotCanGraphite by mutableStateOf(Color(0xFF0D1420))
    private set
var NotCanSurface by mutableStateOf(Color(0xFF131D2A))
    private set
var NotCanSurfaceHigh by mutableStateOf(Color(0xFF1A2635))
    private set
var NotCanSurfaceSoft by mutableStateOf(Color(0xFF101824))
    private set
var NotCanBorder by mutableStateOf(Color(0xFF263548))
    private set
var NotCanOffWhite by mutableStateOf(Color(0xFFF4F7FB))
    private set
var NotCanGray by mutableStateOf(Color(0xFFA7B2C3))
    private set
var NotCanGrayMuted by mutableStateOf(Color(0xFF738096))
    private set

internal fun applyNotCanPalette(darkTheme: Boolean) {
    if (darkTheme) {
        NotCanBlack = Color(0xFF080D15)
        NotCanGraphite = Color(0xFF0D1420)
        NotCanSurface = Color(0xFF131D2A)
        NotCanSurfaceHigh = Color(0xFF1A2635)
        NotCanSurfaceSoft = Color(0xFF101824)
        NotCanBorder = Color(0xFF263548)
        NotCanOffWhite = Color(0xFFF4F7FB)
        NotCanGray = Color(0xFFA7B2C3)
        NotCanGrayMuted = Color(0xFF738096)
    } else {
        NotCanBlack = Color(0xFFFFFCF7)
        NotCanGraphite = Color(0xFFF6EFE6)
        NotCanSurface = Color(0xFFFFFFFF)
        NotCanSurfaceHigh = Color(0xFFF0E7DC)
        NotCanSurfaceSoft = Color(0xFFFAF5EF)
        NotCanBorder = Color(0xFFD8C9B9)
        NotCanOffWhite = Color(0xFF20252C)
        NotCanGray = Color(0xFF5D6672)
        NotCanGrayMuted = Color(0xFF7A746D)
    }
}

// Kept for source compatibility with older screens.
val NotCanBrown = NotCanBrownAccent
