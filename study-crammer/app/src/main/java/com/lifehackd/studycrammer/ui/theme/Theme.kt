package com.lifehackd.studycrammer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// "Cheat code" palette — dark-first, high contrast.
val Lime = Color(0xFFC6FF00)
val LimeDim = Color(0xFF8FB800)
val Charcoal = Color(0xFF121214)
val Surface1 = Color(0xFF1B1B1F)
val Surface2 = Color(0xFF25252B)
val Outline = Color(0xFF3A3A44)
val TextHi = Color(0xFFF4F4F5)
val TextMid = Color(0xFFA1A1AA)
val CodeRed = Color(0xFFFF3B5C)
val Amber = Color(0xFFFFB020)
val Cyan = Color(0xFF00E5FF)

private val CrammerColors = darkColorScheme(
    primary = Lime,
    onPrimary = Charcoal,
    primaryContainer = LimeDim,
    onPrimaryContainer = Charcoal,
    secondary = Cyan,
    onSecondary = Charcoal,
    tertiary = Amber,
    onTertiary = Charcoal,
    background = Charcoal,
    onBackground = TextHi,
    surface = Surface1,
    onSurface = TextHi,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextMid,
    surfaceContainerLowest = Charcoal,
    surfaceContainerLow = Surface1,
    surfaceContainer = Surface1,
    surfaceContainerHigh = Surface2,
    surfaceContainerHighest = Surface2,
    outline = Outline,
    outlineVariant = Outline,
    error = CodeRed,
    onError = Color.White,
)

val Mono = FontFamily.Monospace

private val CrammerType = Typography(
    displaySmall = TextStyle(fontWeight = FontWeight.Black, fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Black, fontSize = 26.sp, lineHeight = 30.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, lineHeight = 26.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 0.3.sp),
    labelMedium = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp),
    labelSmall = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.8.sp),
)

@Composable
fun CrammerTheme(content: @Composable () -> Unit) {
    // Deliberately dark-only: the brand IS the dark "cheat code" look.
    MaterialTheme(colorScheme = CrammerColors, typography = CrammerType, content = content)
}
