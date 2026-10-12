package wojtoteka.nightdrive.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import wojtoteka.nightdrive.domain.PowerUpType
import wojtoteka.nightdrive.render.RenderCache
import wojtoteka.nightdrive.render.RetroCanvas
import wojtoteka.nightdrive.render.TokenArt
import wojtoteka.nightdrive.ui.theme.NDColors
import wojtoteka.nightdrive.ui.theme.NDFonts
import wojtoteka.nightdrive.ui.theme.NDType

/**
 * Draws a road token (see [TokenArt]) into a small square icon: a [RetroCanvas] over the Canvas
 * gives the same ±100-unit world the road uses, so the icon is literally the in-game drawing,
 * frozen mid-spin. Stroke is held at ~1.4dp so small icons don't go hairline-thin.
 */
@Composable
private fun TokenIcon(size: Int, modifier: Modifier, draw: (RetroCanvas, Float) -> Unit) {
    val measurer = rememberTextMeasurer(cacheSize = 4)
    val cache = remember { RenderCache() }
    Canvas(modifier.size(size.dp)) {
        val rc = RetroCanvas(this, measurer, NDFonts.Brand, cache)
        val line = maxOf(11f, 1.4.dp.toPx() / rc.unit)
        draw(rc, line)
    }
}

private const val TOKEN_ICON_SIZE = 122f
private const val COIN_ON_ACCENT = 0xFF0B0B0D

/** The coin, as on the road: yellow gyroscope rings around a "$". [dark] for use on accent fills. */
@Composable
fun CoinIcon(size: Int = 18, modifier: Modifier = Modifier, dark: Boolean = false) {
    TokenIcon(size, modifier) { rc, line ->
        TokenArt.drawCoin(
            rc, 0f, 0f, TOKEN_ICON_SIZE, TokenArt.ICON_SPIN, line,
            if (dark) COIN_ON_ACCENT else TokenArt.COIN_COLOR,
        )
    }
}

/** A power-up token, as on the road: its coloured rings around its badge. */
@Composable
fun PowerUpGlyph(type: PowerUpType, size: Int = 18, modifier: Modifier = Modifier) {
    TokenIcon(size, modifier) { rc, line ->
        TokenArt.drawPowerUp(rc, type, 0f, 0f, TOKEN_ICON_SIZE, TokenArt.ICON_SPIN, line)
    }
}

/** Coin balance: token + odometer digits, no container. */
@Composable
fun CoinReadout(count: Int, modifier: Modifier = Modifier, big: Boolean = false) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        CoinIcon(if (big) 22 else 18)
        Spacer(Modifier.width(8.dp))
        Text(count.toString(), style = if (big) NDType.Readout else NDType.ReadoutSmall, color = NDColors.Text, maxLines = 1)
    }
}

// ---- Line glyphs ----------------------------------------------------------------------------
// Drawn rather than taken from the Material icon set, so they share the stroke weight and square
// caps of the rest of the UI.

private fun glyphStroke(px: Float) = Stroke(px, cap = StrokeCap.Square, join = StrokeJoin.Miter)

/** Right-pointing arrow (→) used as the trailing mark on primary buttons and menu rows. */
@Composable
fun ArrowGlyph(color: Color, size: Dp = 13.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val sw = w * 0.16f
        drawLine(color, Offset(0f, h / 2f), Offset(w * 0.92f, h / 2f), sw, StrokeCap.Square)
        val p = Path().apply {
            moveTo(w * 0.52f, h * 0.12f); lineTo(w * 0.94f, h / 2f); lineTo(w * 0.52f, h * 0.88f)
        }
        drawPath(p, color, style = glyphStroke(sw))
    }
}

@Composable
fun BackGlyph(color: Color = NDColors.Text) {
    Canvas(Modifier.size(16.dp)) {
        val w = size.width; val h = size.height
        val sw = 2.dp.toPx()
        drawLine(color, Offset(w * 0.08f, h / 2f), Offset(w, h / 2f), sw, StrokeCap.Square)
        val p = Path().apply { moveTo(w * 0.5f, h * 0.1f); lineTo(w * 0.08f, h / 2f); lineTo(w * 0.5f, h * 0.9f) }
        drawPath(p, color, style = glyphStroke(sw))
    }
}

@Composable
fun PauseGlyph(color: Color = NDColors.Text) {
    Canvas(Modifier.size(14.dp)) {
        val bw = size.width * 0.3f
        drawRect(color, Offset(size.width * 0.08f, 0f), Size(bw, size.height))
        drawRect(color, Offset(size.width * 0.62f, 0f), Size(bw, size.height))
    }
}

/** Three sliders - the settings mark. */
@Composable
fun SlidersGlyph(color: Color = NDColors.Text) {
    Canvas(Modifier.size(18.dp)) {
        val w = size.width; val h = size.height
        val sw = 1.5.dp.toPx()
        val k = 2.5.dp.toPx()
        val knobs = floatArrayOf(0.28f, 0.72f, 0.5f)
        for (i in 0..2) {
            val y = h * (0.18f + 0.32f * i)
            drawLine(color.copy(alpha = 0.55f), Offset(0f, y), Offset(w, y), sw)
            drawRect(color, Offset(w * knobs[i] - k, y - k), Size(k * 2, k * 2))
        }
    }
}

@Composable
fun CheckGlyph(color: Color, size: Dp = 14.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val p = Path().apply { moveTo(w * 0.08f, h * 0.55f); lineTo(w * 0.4f, h * 0.85f); lineTo(w * 0.94f, h * 0.18f) }
        drawPath(p, color, style = glyphStroke(w * 0.16f))
    }
}

/** Shield outline used for the shield shop items. */
@Composable
fun ShieldGlyph(color: Color, size: Dp = 22.dp, plus: Boolean = false) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.04f)
            lineTo(w * 0.9f, h * 0.2f)
            lineTo(w * 0.9f, h * 0.52f)
            cubicTo(w * 0.9f, h * 0.8f, w * 0.7f, h * 0.94f, w * 0.5f, h * 0.98f)
            cubicTo(w * 0.3f, h * 0.94f, w * 0.1f, h * 0.8f, w * 0.1f, h * 0.52f)
            lineTo(w * 0.1f, h * 0.2f)
            close()
        }
        drawPath(path, color, style = Stroke(w * 0.08f, join = StrokeJoin.Round))
        if (plus) {
            val sw = w * 0.08f
            drawLine(color, Offset(w * 0.5f, h * 0.32f), Offset(w * 0.5f, h * 0.68f), sw)
            drawLine(color, Offset(w * 0.32f, h * 0.5f), Offset(w * 0.68f, h * 0.5f), sw)
        }
    }
}
