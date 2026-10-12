package wojtoteka.nightdrive.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Night Drive UI palette - an instrument cluster at night, not a neon sign. Flat near-black
 * panels, hairline rules, warm white text and a single signal-yellow accent (the coin's colour,
 * the road-marking colour) for anything you can act on. Everything else stays neutral, so colour
 * only shows up where it means something: the accent, a power-up's own colour, success, danger.
 *
 * The logo keeps its original white/magenta/cyan glow - it's the only place those appear.
 */
object NDColors {
    // Surfaces
    val Ink = Color(0xFF07070A)
    val Panel = Color(0xEB0D0D12)        // translucent enough to sit over the world, dark enough to read on
    val PanelSolid = Color(0xFF121218)
    val PanelRaised = Color(0xFF1B1B23)
    val Inset = Color(0xFF09090D)        // preview wells inside cards
    val Scrim = Color(0xD9050507)

    // Rules
    val Line = Color(0x1AFFFFFF)
    val LineStrong = Color(0x33FFFFFF)

    // Accent
    val Accent = Color(0xFFFFD60A)
    val AccentPressed = Color(0xFFE6BF00)
    val OnAccent = Color(0xFF0B0B0D)

    // States
    val Positive = Color(0xFF5BD68A)
    val Danger = Color(0xFFFF4D3D)

    // Text
    val Text = Color(0xFFF2F1EC)
    val TextDim = Color(0xFFA2A2AA)
    val TextFaint = Color(0xFF63636D)

    // Logo only
    val LogoWhite = Color(0xFFF4F6FF)
    val LogoMagenta = Color(0xFFFF3DAE)
    val LogoCyan = Color(0xFF22E9FF)
}
