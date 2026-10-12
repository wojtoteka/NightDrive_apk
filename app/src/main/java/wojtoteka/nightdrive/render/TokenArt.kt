package wojtoteka.nightdrive.render

import wojtoteka.nightdrive.domain.PowerUpType
import kotlin.math.cos
import kotlin.math.sin

/**
 * The road pickups, drawn the way the original microScript game drew its coin: a wireframe
 * "gyroscope" of two rings - one squashed horizontally by sin(t), the other vertically by cos(t) -
 * with a badge in the middle. Coins and power-ups share the exact same construction and differ
 * only in colour and badge, so the whole road reads as one family of tokens.
 *
 * Used by both the world renderer (world units, animated) and the UI icons (a [RetroCanvas] laid
 * over a tiny Canvas, frozen at [ICON_SPIN]), so a coin in the shop is the same drawing as a coin
 * on the road.
 */
object TokenArt {

    /** Original coin colour: plain CSS "yellow". */
    const val COIN_COLOR = 0xFFFFFF00

    /** Spin phase used for static icons: one ring narrowed to ~45%, the other just shy of round. */
    const val ICON_SPIN = 0.47f

    /** Same period as the original (system.time() / 150). */
    fun spinAt(animMs: Float): Float = animMs / 150f

    /**
     * Two spinning rings of diameter 1.5 × [size] at ([x], [y]). The ring alpha follows
     * [RetroCanvas.alpha] so pop-out fades carry through; [lineWidth] is in world units.
     */
    fun drawRings(rc: RetroCanvas, x: Float, y: Float, size: Float, spin: Float, color: Long, lineWidth: Float) {
        val d = 1.5f * size
        rc.lineWidth = lineWidth
        rc.drawRound(x, y, d * sin(spin), d, color)
        rc.drawRound(x, y, d, d * cos(spin), color)
    }

    /** Coin = rings + "$" in the original Edunline face. */
    fun drawCoin(rc: RetroCanvas, x: Float, y: Float, size: Float, spin: Float, lineWidth: Float, color: Long = COIN_COLOR) {
        drawRings(rc, x, y, size, spin, color, lineWidth)
        rc.drawText("$", x, y, size / 1.3f, color)
    }

    /** Power-up = the same rings in the power-up's colour, with its own badge instead of "$". */
    fun drawPowerUp(rc: RetroCanvas, type: PowerUpType, x: Float, y: Float, size: Float, spin: Float, lineWidth: Float) {
        val col = type.color
        drawRings(rc, x, y, size, spin, col, lineWidth)
        drawBadge(rc, type, x, y, size, col, lineWidth)
    }

    /** Badges are line art at the same stroke as the rings, sized to sit where the "$" sits. */
    private fun drawBadge(rc: RetroCanvas, type: PowerUpType, x: Float, y: Float, size: Float, col: Long, lineWidth: Float) {
        val s = size * 0.26f
        rc.lineWidth = lineWidth * 1.15f
        when (type) {
            PowerUpType.SHIELD -> rc.drawPolyline(
                true,
                x - 1.3f * s, y + 1.4f * s,
                x + 1.3f * s, y + 1.4f * s,
                x + 1.3f * s, y + 0.1f * s,
                x, y - 1.5f * s,
                x - 1.3f * s, y + 0.1f * s,
                color = col,
            )
            PowerUpType.MAGNET -> {
                // Horseshoe: two legs joined by a rounded base, white tips like a real magnet.
                rc.drawPolyline(
                    false,
                    x - 1.1f * s, y + 1.4f * s,
                    x - 1.1f * s, y - 0.3f * s,
                    x - 0.75f * s, y - 1.05f * s,
                    x, y - 1.35f * s,
                    x + 0.75f * s, y - 1.05f * s,
                    x + 1.1f * s, y - 0.3f * s,
                    x + 1.1f * s, y + 1.4f * s,
                    color = col,
                )
                rc.drawLine(x - 1.1f * s, y + 1.4f * s, x - 1.1f * s, y + 0.9f * s, 0xFFFFFFFF)
                rc.drawLine(x + 1.1f * s, y + 1.4f * s, x + 1.1f * s, y + 0.9f * s, 0xFFFFFFFF)
            }
            PowerUpType.SLOWMO -> {
                rc.drawRound(x, y, 2.9f * s, 2.9f * s, col)
                rc.drawLine(x, y, x, y + 1.0f * s, col)
                rc.drawLine(x, y, x + 0.75f * s, y - 0.3f * s, col)
            }
            PowerUpType.DOUBLE -> rc.drawText("x2", x, y, size / 1.6f, col)
            PowerUpType.COIN_RUSH -> rc.drawText("$$", x, y, size / 1.6f, col)
        }
    }
}
