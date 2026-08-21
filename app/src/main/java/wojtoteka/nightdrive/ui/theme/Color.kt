package wojtoteka.nightdrive.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Night Drive neo-synthwave palette. Deep midnight bases with two neon accents (cyan + magenta),
 * a warm coin gold and a sunset gradient. Restrained on purpose - glow is used sparingly so the UI
 * reads as a crafted product rather than an "everything glows" AI look.
 */
object NDColors {
    // Bases
    val Background = Color(0xFF06070F)
    val BackgroundElevated = Color(0xFF0B0C1B)
    val Surface = Color(0xFF10122A)
    val SurfaceHi = Color(0xFF171A38)
    val Outline = Color(0xFF262A4F)

    // Neons
    val Cyan = Color(0xFF22E9FF)
    val CyanDim = Color(0xFF0E7C90)
    val Magenta = Color(0xFFFF3DAE)
    val MagentaDim = Color(0xFF9A2E74)
    val Violet = Color(0xFF9B6BFF)
    val Gold = Color(0xFFFFD23F)
    val Green = Color(0xFF5DE0A0)
    val Red = Color(0xFFFF5A6E)

    // Sunset stops
    val SunTop = Color(0xFFFFE7A6)
    val SunMid = Color(0xFFFF8A3D)
    val SunLow = Color(0xFFFF3DAE)
    val SunDeep = Color(0xFF7A2BE0)

    // Text
    val TextPrimary = Color(0xFFF4F6FF)
    val TextSecondary = Color(0xFFB4BBDD)
    val TextMuted = Color(0xFF7B84AC)
}

@Immutable
data class NDPalette(
    val background: Color = NDColors.Background,
    val surface: Color = NDColors.Surface,
    val surfaceHi: Color = NDColors.SurfaceHi,
    val cyan: Color = NDColors.Cyan,
    val magenta: Color = NDColors.Magenta,
    val violet: Color = NDColors.Violet,
    val gold: Color = NDColors.Gold,
    val green: Color = NDColors.Green,
    val red: Color = NDColors.Red,
    val textPrimary: Color = NDColors.TextPrimary,
    val textSecondary: Color = NDColors.TextSecondary,
    val textMuted: Color = NDColors.TextMuted,
)
