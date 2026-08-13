package org.sovereignhq.sleepwave.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.sovereignhq.sleepwave.data.EventKind

/**
 * Dark only, and deliberately so.
 *
 * The scene: someone in an unlit bedroom at 00:30 setting an alarm, and the same person at 07:05
 * still in bed, squinting, scrolling through what the phone heard. A light theme is hostile in both
 * moments, and Dynamic Color is declined for the same reason - a wallpaper-derived pastel scheme
 * would undo the one property this app needs, which is being dim enough to look at in the dark.
 *
 * Colour strategy is Restrained: near-black surfaces, one indigo accent for anything actionable,
 * and a small set of semantic hues that only ever encode data (sleep stage, event class). No hue
 * is used decoratively.
 */

// Surfaces, dimmest first. Elevation is carried by tonal steps, not shadows.
private val Ink0 = Color(0xFF08090F)   // background
private val Ink1 = Color(0xFF11131F)   // surface
private val Ink2 = Color(0xFF1A1D2E)   // surface variant, raised rows
private val Ink3 = Color(0xFF272B41)   // outline

private val Indigo = Color(0xFF8B9CF9)
private val IndigoInk = Color(0xFF0A0C16)
private val IndigoDeep = Color(0xFF3B5BDB)
private val Mint = Color(0xFF6EE7B7)
private val Amber = Color(0xFFF7B267)
private val Rose = Color(0xFFF08A8A)

private val Ink = Color(0xFFEAECF8)      // body text, 15.8:1 on Ink0
private val InkMuted = Color(0xFF9AA2C4) // secondary text, 7.1:1 on Ink0 - not a light gray

val SleepWaveColors = darkColorScheme(
    primary = Indigo,
    onPrimary = IndigoInk,
    primaryContainer = IndigoDeep,
    onPrimaryContainer = Ink,
    secondary = Mint,
    onSecondary = Color(0xFF04241A),
    secondaryContainer = Color(0xFF14332A),
    onSecondaryContainer = Mint,
    tertiary = Amber,
    onTertiary = Color(0xFF2E1C06),
    background = Ink0,
    onBackground = Ink,
    surface = Ink1,
    onSurface = Ink,
    surfaceVariant = Ink2,
    onSurfaceVariant = InkMuted,
    surfaceContainerHighest = Ink2,
    outline = Ink3,
    outlineVariant = Ink2,
    error = Rose,
    onError = Color(0xFF2A0808),
    scrim = Color(0xFF000000)
)

/**
 * Data colours. Deliberately outside the Material roles because they encode meaning rather than
 * emphasis, exactly like chart series: a sleep stage is not "primary" or "tertiary", it is deep.
 */
object DataColors {
    val stageAwake = Amber
    val stageLight = Indigo
    val stageDeep = IndigoDeep

    val snore = Indigo
    val voice = Mint
    val rumble = Amber
    val thump = Rose
    val other = InkMuted

    fun forEvent(kind: EventKind): Color = when (kind) {
        EventKind.SNORE -> snore
        EventKind.VOICE -> voice
        EventKind.RUMBLE -> rumble
        EventKind.THUMP -> thump
        EventKind.OTHER -> other
    }
}

/**
 * The Material type scale, tuned once here rather than per screen. Ratio is tight (~1.2) because
 * this is product UI with a lot of small labels; only the clock and the big loudness readouts get
 * display sizes, and they earn it by being read from a metre away in the dark.
 */
private val typography = Typography(
    displayLarge = TextStyle(fontSize = 60.sp, lineHeight = 64.sp, fontWeight = FontWeight.Light, letterSpacing = (-1.5).sp),
    displayMedium = TextStyle(fontSize = 42.sp, lineHeight = 48.sp, fontWeight = FontWeight.Light, letterSpacing = (-1).sp),
    displaySmall = TextStyle(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Normal),
    headlineMedium = TextStyle(fontSize = 25.sp, lineHeight = 30.sp, fontWeight = FontWeight.Medium),
    headlineSmall = TextStyle(fontSize = 21.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium),
    titleLarge = TextStyle(fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp)
)

@Composable
fun SleepWaveTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SleepWaveColors, typography = typography, content = content)
}
