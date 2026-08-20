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
import wojtoteka.nightdrive.ui.theme.NDColors
import wojtoteka.nightdrive.ui.theme.NDFonts
import wojtoteka.nightdrive.ui.theme.Radius

/** A small gold coin token with a "$". */
@Composable
fun CoinIcon(size: Int = 18, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(NDColors.Gold, Color(0xFFE0A21A))))
            .border(BorderStroke(1.dp, Color(0xFFFFF0B8)), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text("$", fontFamily = NDFonts.Ui, fontSize = (size * 0.62f).sp, color = Color(0xFF5A3B00))
    }
}

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

/** Compact power-up symbol for the in-game HUD panel and pickup toast. */
@Composable
fun PowerUpGlyph(type: wojtoteka.nightdrive.domain.PowerUpType, size: Int = 18, modifier: Modifier = Modifier) {
    val color = Color(type.color)
    when (type) {
        wojtoteka.nightdrive.domain.PowerUpType.DOUBLE ->
            Box(modifier.size(size.dp), contentAlignment = Alignment.Center) {
                Text("×2", fontFamily = NDFonts.Display, fontSize = (size * 0.72f).sp, color = color)
            }
        wojtoteka.nightdrive.domain.PowerUpType.COIN_RUSH ->
            Box(modifier.size(size.dp), contentAlignment = Alignment.Center) {
                Text("$×2", fontFamily = NDFonts.Display, fontSize = (size * 0.58f).sp, color = color)
            }
        wojtoteka.nightdrive.domain.PowerUpType.SHIELD -> ShieldIcon(size, color, modifier)
        else -> Canvas(modifier.size(size.dp)) {
            val w = this.size.width; val h = this.size.height
            when (type) {
                wojtoteka.nightdrive.domain.PowerUpType.MAGNET -> {
                    // U-shaped magnet
                    drawArc(color, 180f, 180f, useCenter = false,
                        topLeft = Offset(w * 0.18f, h * 0.16f),
                        size = androidx.compose.ui.geometry.Size(w * 0.64f, h * 0.64f),
                        style = Stroke(width = w * 0.22f))
                    drawRect(color, Offset(w * 0.18f, h * 0.48f), androidx.compose.ui.geometry.Size(w * 0.22f, h * 0.28f))
                    drawRect(color, Offset(w * 0.60f, h * 0.48f), androidx.compose.ui.geometry.Size(w * 0.22f, h * 0.28f))
                    drawRect(Color.White, Offset(w * 0.18f, h * 0.70f), androidx.compose.ui.geometry.Size(w * 0.22f, h * 0.12f))
                    drawRect(Color.White, Offset(w * 0.60f, h * 0.70f), androidx.compose.ui.geometry.Size(w * 0.22f, h * 0.12f))
                }
                wojtoteka.nightdrive.domain.PowerUpType.SLOWMO -> {
                    drawCircle(color, radius = w * 0.42f, style = Stroke(width = w * 0.11f))
                    drawLine(color, Offset(w * 0.5f, h * 0.5f), Offset(w * 0.5f, h * 0.22f), strokeWidth = w * 0.1f)
                    drawLine(color, Offset(w * 0.5f, h * 0.5f), Offset(w * 0.7f, h * 0.56f), strokeWidth = w * 0.1f)
                }
                else -> {}
            }
        }
    }
}
