package wojtoteka.nightdrive.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Draw-pass caches that live across frames (owned by the composition, not the per-frame
 * [RetroCanvas]). Reusing the same [Brush] instance lets Compose reuse its native gradient shader,
 * and reusing [Stroke] objects removes the per-call allocation on every stroked draw. Only ever
 * touched from the UI thread's draw pass.
 */
class RenderCache {
    private val strokeWidths = FloatArray(12)
    private val strokes = arrayOfNulls<Stroke>(12)
    private var strokeCount = 0

    fun stroke(width: Float): Stroke {
        for (i in 0 until strokeCount) if (strokeWidths[i] == width) return strokes[i]!!
        val s = Stroke(width)
        if (strokeCount < strokes.size) {
            strokeWidths[strokeCount] = width
            strokes[strokeCount] = s
            strokeCount++
        }
        return s
    }

    private val roundStrokes = HashMap<Float, Stroke>()

    /** Stroke with round caps/joins, so polyline corners don't spike at small sizes. */
    fun roundStroke(width: Float): Stroke {
        if (roundStrokes.size > 32) roundStrokes.clear()
        return roundStrokes.getOrPut(width) { Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round) }
    }

    private data class HGradientKey(val left: Long, val right: Long, val startX: Float, val endX: Float)
    private val hGradients = HashMap<HGradientKey, Brush>()

    fun horizontalGradient(left: Long, right: Long, startX: Float, endX: Float): Brush {
        if (hGradients.size > 16) hGradients.clear()
        return hGradients.getOrPut(HGradientKey(left, right, startX, endX)) {
            Brush.horizontalGradient(listOf(Color(left), Color(right)), startX = startX, endX = endX)
        }
    }

    private data class GradientKey(val top: Long, val bottom: Long, val startY: Float, val endY: Float)
    private val gradients = HashMap<GradientKey, Brush>()

    fun verticalGradient(top: Long, bottom: Long, startY: Float, endY: Float): Brush {
        if (gradients.size > 64) gradients.clear() // size changes can strand stale keys; keep it bounded
        return gradients.getOrPut(GradientKey(top, bottom, startY, endY)) {
            Brush.verticalGradient(listOf(Color(top), Color(bottom)), startY = startY, endY = endY)
        }
    }
}

/**
 * A thin drawing adapter that recreates the original engine's coordinate model on top of a Compose
 * [DrawScope]: origin at the centre, y-axis pointing up, and a fixed virtual height of 200 units
 * (the shorter screen dimension). This lets the world be drawn with the exact same maths as the
 * source, so cars/coins/road look identical while everything else is rebuilt natively.
 */
class RetroCanvas(
    private val ds: DrawScope,
    private val textMeasurer: TextMeasurer,
    private val bodyFont: FontFamily,
    private val cache: RenderCache,
) {
    val widthPx: Float = ds.size.width
    val heightPx: Float = ds.size.height
    val unit: Float = min(widthPx, heightPx) / 200f
    val halfWidthUnits: Float = (widthPx / unit) / 2f
    private val cx = widthPx / 2f
    private val cy = heightPx / 2f

    var alpha: Float = 1f
    var lineWidth: Float = 1f
    var additive: Boolean = false

    private val blend: BlendMode get() = if (additive) BlendMode.Plus else BlendMode.SrcOver

    fun sx(x: Float): Float = cx + x * unit
    fun sy(y: Float): Float = cy - y * unit

    fun reset() { alpha = 1f; lineWidth = 1f; additive = false }

    private fun c(argb: Long) = Color(argb)

    fun fillRect(x: Float, y: Float, w: Float, h: Float, color: Long) =
        ds.drawRect(c(color), Offset(sx(x - w / 2f), sy(y + h / 2f)), Size(w * unit, h * unit), alpha, blendMode = blend)

    fun fillRect(x: Float, y: Float, w: Float, h: Float, color: Color) =
        ds.drawRect(color, Offset(sx(x - w / 2f), sy(y + h / 2f)), Size(w * unit, h * unit), alpha, blendMode = blend)

    fun drawRect(x: Float, y: Float, w: Float, h: Float, color: Long) =
        ds.drawRect(c(color), Offset(sx(x - w / 2f), sy(y + h / 2f)), Size(w * unit, h * unit), alpha, style = cache.stroke(lineWidth * unit), blendMode = blend)

    fun fillRound(x: Float, y: Float, w: Float, h: Float, color: Long) {
        val aw = abs(w); val ah = abs(h)
        ds.drawOval(c(color), Offset(sx(x - aw / 2f), sy(y + ah / 2f)), Size(aw * unit, ah * unit), alpha, blendMode = blend)
    }

    fun drawRound(x: Float, y: Float, w: Float, h: Float, color: Long) {
        val aw = abs(w); val ah = abs(h)
        ds.drawOval(c(color), Offset(sx(x - aw / 2f), sy(y + ah / 2f)), Size(aw * unit, ah * unit), alpha, style = cache.stroke(lineWidth * unit), blendMode = blend)
    }

    fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float, color: Long) =
        ds.drawLine(c(color), Offset(sx(x1), sy(y1)), Offset(sx(x2), sy(y2)), lineWidth * unit, StrokeCap.Round, alpha = alpha, blendMode = blend)

    fun fillPolygon(vararg pts: Float, color: Long) {
        if (pts.size < 6) return
        val path = Path().apply {
            moveTo(sx(pts[0]), sy(pts[1]))
            var i = 2
            while (i + 1 < pts.size) { lineTo(sx(pts[i]), sy(pts[i + 1])); i += 2 }
            close()
        }
        ds.drawPath(path, c(color), alpha, blendMode = blend)
    }

    /** Stroked polyline through (x, y) pairs, optionally closed back to the first point. */
    fun drawPolyline(closed: Boolean, vararg pts: Float, color: Long) {
        if (pts.size < 4) return
        val path = Path().apply {
            moveTo(sx(pts[0]), sy(pts[1]))
            var i = 2
            while (i + 1 < pts.size) { lineTo(sx(pts[i]), sy(pts[i + 1])); i += 2 }
            if (closed) close()
        }
        ds.drawPath(path, c(color), alpha, style = cache.roundStroke(lineWidth * unit), blendMode = blend)
    }

    /** Vertical gradient rectangle in world coords (used for sky/road bands). */
    fun fillVerticalGradient(x: Float, y: Float, w: Float, h: Float, topColor: Long, bottomColor: Long) =
        fillVerticalGradientPart(x, y, w, h, y, h, topColor, bottomColor)

    /**
     * Draws only the (x,y,w,h) slice of a vertical gradient whose full span is (gradY, gradH) -
     * lets a caller skip regions an opaque later layer covers anyway, with pixel-identical output
     * (the gradient's colour ramp is anchored to the full span, not the drawn slice).
     */
    fun fillVerticalGradientPart(
        x: Float, y: Float, w: Float, h: Float,
        gradY: Float, gradH: Float, topColor: Long, bottomColor: Long,
    ) {
        val brush = cache.verticalGradient(topColor, bottomColor, sy(gradY + gradH / 2f), sy(gradY - gradH / 2f))
        ds.drawRect(brush, Offset(sx(x - w / 2f), sy(y + h / 2f)), Size(w * unit, h * unit), alpha, blendMode = blend)
    }

    /**
     * Horizontal gradient rectangle in world coords. The colour ramp runs from world x [gradX0] to
     * [gradX1] and holds the end colours beyond them.
     */
    fun fillHorizontalGradient(
        x: Float, y: Float, w: Float, h: Float,
        gradX0: Float, gradX1: Float, leftColor: Long, rightColor: Long,
    ) {
        val brush = cache.horizontalGradient(leftColor, rightColor, sx(gradX0), sx(gradX1))
        ds.drawRect(brush, Offset(sx(x - w / 2f), sy(y + h / 2f)), Size(w * unit, h * unit), alpha, blendMode = blend)
    }

    fun drawText(text: String, x: Float, y: Float, worldSize: Float, color: Long, font: FontFamily = bodyFont) {
        // Quantize the size to 0.5sp steps (~1px, invisible in motion): the depth scale varies
        // continuously, and un-quantized sizes would defeat the TextMeasurer layout cache - full
        // text layout per coin per frame is the single most expensive draw call in the game.
        val rawSp = with(ds) { (worldSize * unit).toSp() }
        val fontSizeSp = ((rawSp.value * 2f).roundToInt() / 2f).sp
        val layout = textMeasurer.measure(text, TextStyle(fontFamily = font, fontSize = fontSizeSp))
        val tl = Offset(sx(x) - layout.size.width / 2f, sy(y) - layout.size.height / 2f)
        ds.drawText(layout, color = c(color), topLeft = tl, alpha = alpha)
    }

    companion object {
        /** hsl(h,100%,50%) -> ARGB long, matching the enemy colour formula. */
        fun hsl100(h: Int): Long {
            val hue = ((h % 360) + 360) % 360
            val cc = 1f
            val x = cc * (1f - abs((hue / 60f) % 2f - 1f))
            val (r, g, b) = when {
                hue < 60 -> Triple(cc, x, 0f)
                hue < 120 -> Triple(x, cc, 0f)
                hue < 180 -> Triple(0f, cc, x)
                hue < 240 -> Triple(0f, x, cc)
                hue < 300 -> Triple(x, 0f, cc)
                else -> Triple(cc, 0f, x)
            }
            val ri = (r * 255).toInt(); val gi = (g * 255).toInt(); val bi = (b * 255).toInt()
            return 0xFF000000 or (ri.toLong() shl 16) or (gi.toLong() shl 8) or bi.toLong()
        }
    }
}
