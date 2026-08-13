package org.sovereignhq.sleepwave.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Dark only, and deliberately so: every screen in this app is looked at either in a dark bedroom
 * or within a minute of waking up. A light theme would be actively unpleasant in both cases.
 */

val NightBg = Color(0xFF0B0D1A)
val NightSurface = Color(0xFF141A33)
val NightSurfaceHigh = Color(0xFF1E2545)
val NightBorder = Color(0xFF2A3357)

val Indigo = Color(0xFF8B9CF9)
val IndigoDeep = Color(0xFF3B5BDB)
val Mint = Color(0xFF6EE7B7)
val Amber = Color(0xFFF7A85C)
val Rose = Color(0xFFF08A8A)

val TextPrimary = Color(0xFFE9EBF7)
val TextMuted = Color(0xFF8A92B8)

/** Stage colours, reused by the hypnogram, the legend and the trend charts. */
val StageAwake = Amber
val StageLight = Indigo
val StageDeep = IndigoDeep

private val colors = darkColorScheme(
    primary = Indigo,
    onPrimary = Color(0xFF0B0D1A),
    primaryContainer = IndigoDeep,
    onPrimaryContainer = TextPrimary,
    secondary = Mint,
    onSecondary = Color(0xFF06281C),
    tertiary = Amber,
    onTertiary = Color(0xFF33200A),
    background = NightBg,
    onBackground = TextPrimary,
    surface = NightSurface,
    onSurface = TextPrimary,
    surfaceVariant = NightSurfaceHigh,
    onSurfaceVariant = TextMuted,
    outline = NightBorder,
    error = Rose,
    onError = Color(0xFF2A0A0A)
)

private val typography = Typography(
    displayLarge = TextStyle(fontSize = 64.sp, fontWeight = FontWeight.Light, letterSpacing = (-2).sp),
    displayMedium = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Light, letterSpacing = (-1).sp),
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Medium),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp)
)

@Composable
fun SleepWaveTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
