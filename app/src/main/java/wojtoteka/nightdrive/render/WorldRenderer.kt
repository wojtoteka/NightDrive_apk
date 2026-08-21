package wojtoteka.nightdrive.render

import androidx.compose.ui.text.font.FontFamily
import wojtoteka.nightdrive.domain.PowerUpType
import wojtoteka.nightdrive.domain.Skin
import wojtoteka.nightdrive.domain.Skins
import wojtoteka.nightdrive.domain.WorldTheme
import wojtoteka.nightdrive.domain.WorldThemes
import wojtoteka.nightdrive.engine.Coin
import wojtoteka.nightdrive.engine.Enemy
import wojtoteka.nightdrive.engine.GameEngine
import wojtoteka.nightdrive.engine.GamePhase
import wojtoteka.nightdrive.engine.PowerUp
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Draws the synthwave world (sky, sun, road grid, stars, cars, coins, power-ups, particles) using
 * [RetroCanvas]. The car/coin/power-up geometry is a faithful port of the original vector drawing;
 * the sun, glows and particles are new polish that don't touch gameplay.
 */
class WorldRenderer(private val coinFont: FontFamily, private val symbolFont: FontFamily) {

    fun render(rc: RetroCanvas, engine: GameEngine, animMs: Float) {
        val theme = themeFor(engine.profile.selectedTheme)
        drawBackground(rc, engine, animMs, theme)
        if (engine.phase != GamePhase.MENU) {
            // Painter's algorithm: farthest objects (highest y) first, so anything nearer occludes
            // what's behind it - otherwise a coin near the horizon plasters over a car in the near
            // field and looks like it's stuck inside the car. The player is the nearest object to
            // the camera, so it's always drawn last and occludes whatever it's driving over.
            depthOrder.clear()
            for (i in engine.enemies.indices) depthOrder.add(engine.enemies[i])
            for (i in engine.coins.indices) depthOrder.add(engine.coins[i])
            for (i in engine.powerups.indices) depthOrder.add(engine.powerups[i])
            depthOrder.sortWith(depthComparator)
            for (i in depthOrder.indices) when (val obj = depthOrder[i]) {
                is Enemy -> drawEnemy(rc, obj)
                is Coin -> drawCoin(rc, obj, animMs)
                is PowerUp -> drawPowerUp(rc, obj, animMs)
            }
            drawPlayer(rc, engine, animMs)
            drawParticles(rc, engine)
        }
        rc.reset()
    }

    private val depthOrder = ArrayList<Any>(24)
    private val depthComparator = Comparator<Any> { a, b -> depthY(b).compareTo(depthY(a)) }

    // Catalog lookups are list scans; the ids only change on a shop action, so remember the last.
    private var cachedThemeId: String? = null
    private var cachedTheme: WorldTheme = WorldThemes.all.first()
    private var cachedSkinId: String? = null
    private var cachedSkin: Skin = Skins.all.first()

    private fun themeFor(id: String): WorldTheme {
        if (id != cachedThemeId) { cachedTheme = WorldThemes.byId(id); cachedThemeId = id }
        return cachedTheme
    }

    private fun skinFor(id: String): Skin {
        if (id != cachedSkinId) { cachedSkin = Skins.byId(id); cachedSkinId = id }
        return cachedSkin
    }

    private fun depthY(obj: Any): Float = when (obj) {
        is Enemy -> obj.y
        is Coin -> obj.y
        is PowerUp -> obj.y
        else -> 0f
    }

    // ---- Background ------------------------------------------------------------------------

    private fun drawBackground(rc: RetroCanvas, engine: GameEngine, animMs: Float, theme: WorldTheme) {
        val w = rc.halfWidthUnits * 2f + 40f
        rc.reset()
        // Night sky. The opaque road gradient painted below covers y in [-91, 21], so the sky is
        // only filled where it can actually be seen: above the road and in the strip beneath it
        // (both with a few units of overlap for safety). The gradient ramp still spans the full
        // 210-unit sky, so every visible pixel is identical - this just skips filling roughly
        // half the screen twice every frame.
        rc.fillVerticalGradientPart(0f, 59.5f, w, 91f, 0f, 210f, theme.skyTop, theme.skyBottom)
        rc.fillVerticalGradientPart(0f, -96f, w, 18f, 0f, 210f, theme.skyTop, theme.skyBottom)
        // Stars (above the horizon only)
        rc.alpha = 1f
        for (i in engine.stars.indices) {
            val s = engine.stars[i]
            if (s.y <= 22f) continue
            val tw = 0.6f + 0.4f * sin(animMs / 400f + s.x)
            rc.alpha = 0.5f + 0.4f * tw
            rc.fillRect(s.x, s.y, 1.4f, 1.4f, theme.star)
        }
        rc.alpha = 1f
        // Sun on the horizon
        drawSun(rc, animMs, theme)
        // Horizon glow
        rc.additive = true
        rc.alpha = 0.5f
        rc.fillVerticalGradient(0f, 24f, w, 22f, theme.horizonGlow, theme.horizonGlow and 0x00FFFFFF)
        rc.additive = false
        rc.alpha = 1f
        // Road surface
        rc.fillVerticalGradient(0f, -35f, w, 112f, theme.roadTop, theme.roadBottom)
        // Grid
        drawGrid(rc, animMs, theme)
        // Neon horizon + near edges
        rc.lineWidth = 1.1f
        rc.alpha = 0.9f
        rc.drawLine(-w / 2f, 20f, w / 2f, 20f, theme.horizonLine)
        rc.alpha = 0.7f
        rc.drawLine(-w / 2f, -90f, w / 2f, -90f, theme.horizonLine)
        rc.alpha = 1f
    }

    // The sun's band/slat geometry is constant and the band colours depend only on the theme, so
    // both are computed once (per theme for colours) instead of 24 lerps + 29 sqrts every frame.
    private val sunBandY = FloatArray(SUN_BANDS)
    private val sunBandChord = FloatArray(SUN_BANDS)
    private val sunSlatY = FloatArray(SUN_SLATS)
    private val sunSlatChord = FloatArray(SUN_SLATS)
    private val sunBandColors = LongArray(SUN_BANDS)
    private var sunColorThemeId: String? = null

    init {
        for (k in 0 until SUN_BANDS) {
            val t = k / (SUN_BANDS - 1f)
            val yy = (SUN_CY + SUN_R) - t * (2f * SUN_R)
            val dy = yy - SUN_CY
            sunBandY[k] = yy
            sunBandChord[k] = 2f * sqrt(max(0f, SUN_R * SUN_R - dy * dy))
        }
        for (s in 0 until SUN_SLATS) {
            val t = 0.44f + s * 0.12f
            val yy = (SUN_CY + SUN_R) - t * (2f * SUN_R)
            val dy = yy - SUN_CY
            sunSlatY[s] = yy
            sunSlatChord[s] = 2f * sqrt(max(0f, SUN_R * SUN_R - dy * dy))
        }
    }

    private fun drawSun(rc: RetroCanvas, animMs: Float, theme: WorldTheme) {
        if (sunColorThemeId != theme.id) {
            for (k in 0 until SUN_BANDS) {
                val t = k / (SUN_BANDS - 1f)
                sunBandColors[k] = lerpArgb(theme.sunTop, if (t < 0.5f) theme.sunMid else theme.sunLow, t)
            }
            sunColorThemeId = theme.id
        }
        // outer glow
        rc.additive = true
        rc.alpha = 0.20f + 0.05f * sin(animMs / 700f)
        rc.fillRound(0f, SUN_CY, SUN_R * 3.0f, SUN_R * 3.0f, theme.sunGlow)
        rc.additive = false
        // gradient disc from horizontal bands
        rc.alpha = 0.95f
        val bandH = (2f * SUN_R / SUN_BANDS) * 1.2f
        for (k in 0 until SUN_BANDS) {
            if (sunBandChord[k] <= 0f) continue
            rc.fillRound(0f, sunBandY[k], sunBandChord[k], bandH, sunBandColors[k])
        }
        // slats in the lower half (sky showing through the retro sun)
        rc.alpha = 1f
        for (s in 0 until SUN_SLATS) {
            rc.fillRound(0f, sunSlatY[s], sunSlatChord[s] * 1.02f, 1.4f + s * 0.9f, theme.sunSlat)
        }
    }

    private fun drawGrid(rc: RetroCanvas, animMs: Float, theme: WorldTheme) {
        rc.alpha = 0.32f
        rc.lineWidth = 0.5f
        val off = (animMs * 0.02f) % 20f
        // Longitudinal converging rails (scroll left, seamless every 20u near-spacing)
        var i = -34
        while (i <= 34) {
            val xNear = 20f * i - 3f * off
            val xFar = 7f * i - off
            rc.drawLine(xNear, -90f, xFar, 20f, theme.grid)
            i++
        }
        // Faint lateral guides for depth
        rc.alpha = 0.14f
        val w = rc.halfWidthUnits * 2f + 40f
        for (yy in intArrayOf(-60, -30, 0)) {
            rc.drawLine(-w / 2f, yy.toFloat(), w / 2f, yy.toFloat(), theme.grid)
        }
        rc.alpha = 1f
    }

    // ---- Cars ------------------------------------------------------------------------------

    private fun drawPlayer(rc: RetroCanvas, engine: GameEngine, animMs: Float) {
        if (engine.phase == GamePhase.GAME_OVER) return
        // Classic arcade blink while the post-revive grace window is active.
        if (engine.invulnerable && (animMs / 130f).toInt() % 2 == 0) return
        val skin = skinFor(engine.profile.selectedSkin)
        drawCar(rc, engine.player.x, engine.player.y, engine.player.bounce, skin.bodyColor, skin.accentColor)
    }

    private fun drawEnemy(rc: RetroCanvas, e: Enemy) {
        val col = RetroCanvas.hsl100(e.hue)
        drawCar(rc, e.x, e.y, 0.3f * sin(e.x / 30f), col, col)
    }

    /** Faithful port of the original wireframe car (near face + far face + connecting edges). */
    private fun drawCar(rc: RetroCanvas, baseX: Float, baseY: Float, bounce: Float, nearColor: Long, farColor: Long) {
        rc.reset()
        rc.lineWidth = 1f
        val y = baseY + bounce
        val size1 = 10f - y / 9f
        val size2 = size1 / 1.1f
        val x = baseX
        val y2 = baseY / 1.1f + 5f - (baseY - 20f) / 10f + bounce
        val x2 = baseX / (1.07f - y2 / 700f)

        // far wheels
        rc.drawRound(x2 + 5f * size2 / 2f - 8f * size2 / 2f, y2 + 3f * size2 / 2f - 6f * size2 / 2f + bounce, 1.5f * size2, 1.5f * size2, farColor)
        rc.drawRound(x2 + 5f * size2 / 2f - 2f * size2 / 2f, y2 + 3f * size2 / 2f - 6f * size2 / 2f + bounce, 1.5f * size2, 1.5f * size2, farColor)
        // near wheels
        rc.drawRound(x + 5f * size1 / 2f - 8f * size1 / 2f, y + 3f * size1 / 2f - 6f * size1 / 2f + bounce, 1.5f * size1, 1.5f * size1, nearColor)
        rc.drawRound(x + 5f * size1 / 2f - 2f * size1 / 2f, y + 3f * size1 / 2f - 6f * size1 / 2f + bounce, 1.5f * size1, 1.5f * size1, nearColor)
        // bodies
        rc.drawRect(x, y, 5f * size1, 3f * size1, nearColor)
        rc.drawRect(x2, y2, 5f * size2, 3f * size2, farColor)
        // connecting edges
        rc.drawLine(x + 5f * size1 / 2f, y + 3f * size1 / 2f, x2 + 5f * size2 / 2f, y2 + 3f * size2 / 2f, farColor)
        rc.drawLine(x + 5f * size1 / 2f, y - 3f * size1 / 2f, x2 + 5f * size2 / 2f, y2 - 3f * size2 / 2f, farColor)
        rc.drawLine(x - 5f * size1 / 2f, y + 3f * size1 / 2f, x2 - 5f * size2 / 2f, y2 + 3f * size2 / 2f, farColor)
        rc.drawLine(x - 5f * size1 / 2f, y - 3f * size1 / 2f, x2 - 5f * size2 / 2f, y2 - 3f * size2 / 2f, farColor)
    }

    // ---- Coins -----------------------------------------------------------------------------

    private fun drawCoin(rc: RetroCanvas, c: Coin, animMs: Float) {
        rc.reset()
        // Depth-scaled with the same formula as the pickup hitbox in GameEngine (10 - y/9), so a
        // coin near the horizon is small next to distant cars and grows as it approaches - the
        // visual matches where the game actually considers the coin to be.
        val base = 10f - c.y / 9f
        val size = if (c.popping) base * (1f + (1f - c.pop) * 1.2f) else base
        val a = if (c.popping) c.pop.coerceIn(0f, 1f) else 1f
        val gold = 0xFFFFCF34
        val goldHi = 0xFFFFE896
        val goldEdge = 0xFF7A5200
        val d = 1.35f * size
        // Spin: squash the disc's width like a coin rotating around its vertical axis.
        val spin = kotlin.math.abs(sin(animMs / 180f + c.x * 0.03f))
        val w = d * (0.22f + 0.78f * spin)
        // small soft shadow on the road
        rc.alpha = 0.16f * a
        rc.fillRound(c.x, c.y - size * 0.9f, 1.2f * size, 0.36f * size, 0xFF000000)
        // gold disc + lighter inner face + "$" once the face is turned far enough toward us
        rc.alpha = a
        rc.fillRound(c.x, c.y, w, d, gold)
        rc.fillRound(c.x, c.y, w * 0.66f, d * 0.66f, goldHi)
        if (spin > 0.45f) rc.drawText("$", c.x, c.y, size / 1.7f, goldEdge, symbolFont)
        rc.reset()
    }

    // ---- Power-ups -------------------------------------------------------------------------

    private fun drawPowerUp(rc: RetroCanvas, p: PowerUp, animMs: Float) {
        rc.reset()
        // Depth-scaled with the same formula as the coins, so the whole road reads with one
        // consistent perspective.
        val size = 10f - p.y / 9f
        val col = p.type.color
        val pulse = 1f + 0.05f * sin(animMs / 240f)
        val r = 1.5f * size * pulse
        // small soft shadow on the road
        rc.alpha = 0.16f
        rc.fillRound(p.x, p.y - size * 0.9f, 1.3f * size, 0.38f * size, 0xFF000000)
        // amulet body (filled gem) with an inner sheen and a bright rim
        rc.alpha = 0.95f
        rc.fillRound(p.x, p.y, r, r, col)
        rc.alpha = 0.25f
        rc.fillRound(p.x, p.y, r * 0.72f, r * 0.72f, 0xFFFFFFFF)
        rc.alpha = 1f
        rc.lineWidth = 1f
        rc.drawRound(p.x, p.y, r * 1.1f, r * 1.1f, 0xFFFFFFFF)
        // white symbol on top so each power-up is instantly recognisable
        drawPowerUpSymbol(rc, p.type, p.x, p.y, size * 0.4f, 0xFFFFFFFF)
        rc.reset()
    }

    fun drawPowerUpSymbol(rc: RetroCanvas, type: PowerUpType, x: Float, y: Float, s: Float, col: Long) {
        rc.alpha = 1f
        when (type) {
            PowerUpType.SHIELD ->
                rc.fillPolygon(
                    x - 1.4f * s, y + 1.2f * s, x + 1.4f * s, y + 1.2f * s,
                    x + 1.4f * s, y - 0.1f * s, x, y - 1.6f * s, x - 1.4f * s, y - 0.1f * s,
                    color = col,
                )
            PowerUpType.MAGNET -> {
                rc.fillRect(x - 0.9f * s, y + 0.35f * s, 0.9f * s, 1.9f * s, col)
                rc.fillRect(x + 0.9f * s, y + 0.35f * s, 0.9f * s, 1.9f * s, col)
                rc.fillRect(x, y - 0.85f * s, 2.7f * s, 0.9f * s, col)
                rc.fillRect(x - 0.9f * s, y + 1.05f * s, 0.9f * s, 0.5f * s, 0xFFFFFFFF)
                rc.fillRect(x + 0.9f * s, y + 1.05f * s, 0.9f * s, 0.5f * s, 0xFFFFFFFF)
            }
            PowerUpType.SLOWMO -> {
                rc.lineWidth = 1.2f
                rc.drawRound(x, y, 3f * s, 3f * s, col)
                rc.drawLine(x, y, x, y + 1.1f * s, col)
                rc.drawLine(x, y, x + 0.8f * s, y - 0.2f * s, col)
            }
            PowerUpType.DOUBLE -> rc.drawText("x2", x, y, 2.6f * s, col, symbolFont)
            PowerUpType.COIN_RUSH -> rc.drawText("\$x2", x, y, 2.0f * s, col, symbolFont)
        }
    }

    // ---- Particles -------------------------------------------------------------------------

    private fun drawParticles(rc: RetroCanvas, engine: GameEngine) {
        rc.additive = true
        for (i in engine.particles.indices) {
            val p = engine.particles[i]
            rc.alpha = p.life.coerceIn(0f, 1f)
            rc.fillRound(p.x, p.y, p.size, p.size, p.color)
        }
        rc.reset()
    }

    private fun lerpArgb(a: Long, b: Long, t: Float): Long {
        val tt = t.coerceIn(0f, 1f)
        val ar = (a shr 16 and 0xFF).toInt(); val ag = (a shr 8 and 0xFF).toInt(); val ab = (a and 0xFF).toInt()
        val br = (b shr 16 and 0xFF).toInt(); val bg = (b shr 8 and 0xFF).toInt(); val bb = (b and 0xFF).toInt()
        val r = (ar + (br - ar) * tt).toInt()
        val g = (ag + (bg - ag) * tt).toInt()
        val bl = (ab + (bb - ab) * tt).toInt()
        return 0xFF000000 or (r.toLong() shl 16) or (g.toLong() shl 8) or bl.toLong()
    }

    private companion object {
        const val SUN_CY = 32f
        const val SUN_R = 44f
        const val SUN_BANDS = 24
        const val SUN_SLATS = 5
    }
}
