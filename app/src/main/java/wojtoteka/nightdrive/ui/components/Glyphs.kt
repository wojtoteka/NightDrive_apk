package wojtoteka.nightdrive.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import wojtoteka.nightdrive.render.RenderCache
import wojtoteka.nightdrive.render.RetroCanvas
import wojtoteka.nightdrive.render.TokenArt
import wojtoteka.nightdrive.ui.theme.NDColors
import wojtoteka.nightdrive.ui.theme.NDFonts
import wojtoteka.nightdrive.ui.theme.Radius

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

/** The coin, as on the road: yellow gyroscope rings around a "$". */
@Composable
fun CoinIcon(size: Int = 18, modifier: Modifier = Modifier) {
    TokenIcon(size, modifier) { rc, line ->
        TokenArt.drawCoin(rc, 0f, 0f, TOKEN_ICON_SIZE, TokenArt.ICON_SPIN, line)
    }
}

private const val TOKEN_ICON_SIZE = 122f

/** Coin balance pill: coin token + count. */
@Composable
fun CoinPill(count: Int, modifier: Modifier = Modifier) {
    NDCard(modifier = modifier, shape = Radius.pill) {
        Row(
            Modifier.padding(start = 8.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoinIcon(18)
            Spacer(Modifier.width(8.dp))
            Text(
                count.toString(),
                fontFamily = NDFonts.Display,
                fontSize = 16.sp,
                color = NDColors.Gold,
            )
        }
    }
}

/** Simple shield outline used in the shield-charge HUD. */
@Composable
fun ShieldIcon(size: Int = 16, color: Color = NDColors.Cyan, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size.dp)) {
        val w = this.size.width
        val h = this.size.height
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.5f, h * 0.04f)
            lineTo(w * 0.92f, h * 0.22f)
            lineTo(w * 0.92f, h * 0.55f)
            cubicTo(w * 0.92f, h * 0.82f, w * 0.72f, h * 0.96f, w * 0.5f, h * 1.0f)
            cubicTo(w * 0.28f, h * 0.96f, w * 0.08f, h * 0.82f, w * 0.08f, h * 0.55f)
            lineTo(w * 0.08f, h * 0.22f)
            close()
        }
        drawPath(path, color, style = Stroke(width = w * 0.10f))
    }
}

/** A power-up token, as on the road: its coloured rings around its badge. */
@Composable
fun PowerUpGlyph(type: wojtoteka.nightdrive.domain.PowerUpType, size: Int = 18, modifier: Modifier = Modifier) {
    TokenIcon(size, modifier) { rc, line ->
        TokenArt.drawPowerUp(rc, type, 0f, 0f, TOKEN_ICON_SIZE, TokenArt.ICON_SPIN, line)
    }
}
