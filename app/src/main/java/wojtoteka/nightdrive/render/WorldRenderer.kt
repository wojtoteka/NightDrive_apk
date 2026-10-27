package wojtoteka.nightdrive.render

import wojtoteka.nightdrive.domain.Skin
import wojtoteka.nightdrive.domain.Skins
import wojtoteka.nightdrive.domain.WorldTheme
import wojtoteka.nightdrive.domain.WorldThemes
import wojtoteka.nightdrive.engine.Coin
import wojtoteka.nightdrive.engine.Enemy
import wojtoteka.nightdrive.engine.EnemyKind
import wojtoteka.nightdrive.engine.GameConfig
import wojtoteka.nightdrive.engine.GameEngine
import wojtoteka.nightdrive.engine.GamePhase
import wojtoteka.nightdrive.engine.PowerUp
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** World y of the neon line along the near edge of the road. Screens keep their content above it. */
const val ROAD_NEAR_EDGE_Y = -90f

/**
 * Draws the synthwave world (sky, sun, road grid, stars, cars, coins, power-ups, particles) using
 * [RetroCanvas]. The car and coin geometry is a faithful port of the original vector drawing;
 * the sun, glows and particles are new polish that don't touch gameplay.
 */
class WorldRenderer {

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
                is Enemy -> drawEnemy(rc, obj, animMs)
                is Coin -> drawCoin(rc, obj, animMs)
                is PowerUp -> drawPowerUp(rc, obj, animMs)
            }
            // Fog goes over the traffic but under the player and the speeder markers, so the road
            // ahead disappears while your own car and the warnings stay readable.
            val fog = engine.fogAmount
            if (fog > 0f) drawFog(rc, fog)
            drawPlayer(rc, engine, animMs)
            drawParticles(rc, engine)
            drawSpeederWarnings(rc, engine, animMs)
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
        rc.drawLine(-w / 2f, ROAD_NEAR_EDGE_Y, w / 2f, ROAD_NEAR_EDGE_Y, theme.horizonLine)
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
        val skin = skinFor(engine.profile.selectedSkin)
        drawCar(rc, engine.player.x, engine.player.y, engine.player.bounce, skin.bodyColor, skin.accentColor)
    }

    private fun drawEnemy(rc: RetroCanvas, e: Enemy, animMs: Float) {
        val col = RetroCanvas.hsl100(e.hue)
        val bounce = 0.3f * sin(e.x / 30f)
        if (e.kind == EnemyKind.SPEEDER) drawSpeedStreaks(rc, e, bounce, col)
        drawCar(rc, e.x, e.y, bounce, col, col, e.length)
        if (e.changingLane && (animMs / BLINK_MS).toInt() % 2 == 0) drawBlinker(rc, e, bounce)
    }

    /**
     * Faithful port of the original wireframe car (near face + far face + connecting edges).
     * [length] stretches the body for trucks; at 1 the geometry is exactly the original car.
     */
    private fun drawCar(
        rc: RetroCanvas, baseX: Float, baseY: Float, bounce: Float,
        nearColor: Long, farColor: Long, length: Float = 1f,
    ) {
        rc.reset()
        rc.lineWidth = 1f
        val y = baseY + bounce
        val size1 = 10f - y / 9f
        val size2 = size1 / 1.1f
        val x = baseX
        val y2 = baseY / 1.1f + 5f - (baseY - 20f) / 10f + bounce
        val x2 = baseX / (1.07f - y2 / 700f)
        val half1 = 2.5f * length * size1       // half body length, near face
        val half2 = 2.5f * length * size2       // half body length, far face
        val wheel1 = half1 - size1              // wheel centres sit one size in from each end
        val wheel2 = half2 - size2

        // far wheels
        rc.drawRound(x2 - wheel2, y2 + 3f * size2 / 2f - 6f * size2 / 2f + bounce, 1.5f * size2, 1.5f * size2, farColor)
        rc.drawRound(x2 + wheel2, y2 + 3f * size2 / 2f - 6f * size2 / 2f + bounce, 1.5f * size2, 1.5f * size2, farColor)
        // near wheels
        rc.drawRound(x - wheel1, y + 3f * size1 / 2f - 6f * size1 / 2f + bounce, 1.5f * size1, 1.5f * size1, nearColor)
        rc.drawRound(x + wheel1, y + 3f * size1 / 2f - 6f * size1 / 2f + bounce, 1.5f * size1, 1.5f * size1, nearColor)
        // bodies
        rc.drawRect(x, y, 2f * half1, 3f * size1, nearColor)
        rc.drawRect(x2, y2, 2f * half2, 3f * size2, farColor)
        // connecting edges
        rc.drawLine(x + half1, y + 3f * size1 / 2f, x2 + half2, y2 + 3f * size2 / 2f, farColor)
        rc.drawLine(x + half1, y - 3f * size1 / 2f, x2 + half2, y2 - 3f * size2 / 2f, farColor)
        rc.drawLine(x - half1, y + 3f * size1 / 2f, x2 - half2, y2 + 3f * size2 / 2f, farColor)
        rc.drawLine(x - half1, y - 3f * size1 / 2f, x2 - half2, y2 - 3f * size2 / 2f, farColor)
        if (length > 1f) {
            // Truck: the cab is split off the trailer at the front (traffic drives to the left).
            val cab1 = x - half1 + 1.8f * size1
            val cab2 = x2 - half2 + 1.8f * size2
            rc.drawLine(cab1, y - 3f * size1 / 2f, cab1, y + 3f * size1 / 2f, nearColor)
            rc.drawLine(cab2, y2 - 3f * size2 / 2f, cab2, y2 + 3f * size2 / 2f, farColor)
        }
    }

    /** Amber indicator on the front corner facing the lane the car is about to move into. */
    private fun drawBlinker(rc: RetroCanvas, e: Enemy, bounce: Float) {
        val y = e.y + bounce
        val size = 10f - y / 9f
        val side = if (e.targetY > e.y) 1f else -1f
        val bx = e.x - 2.5f * e.length * size
        val by = y + side * 1.5f * size
        rc.additive = true
        rc.alpha = 0.45f
        rc.fillRound(bx, by, 2.6f * size, 2.6f * size, BLINKER_COLOR)
        rc.alpha = 1f
        rc.fillRound(bx, by, 0.9f * size, 0.9f * size, BLINKER_COLOR)
        rc.reset()
    }

    /** Motion lines trailing a speeder, so it reads as fast even while slow-mo is on. */
    private fun drawSpeedStreaks(rc: RetroCanvas, e: Enemy, bounce: Float, color: Long) {
        val y = e.y + bounce
        val size = 10f - y / 9f
        val tail = e.x + 2.5f * size
        rc.reset()
        rc.lineWidth = 0.6f
        for (k in -1..1) {
            val outer = if (k == 0) 0f else 1f
            rc.alpha = 0.55f - 0.15f * outer
            val ly = y + k * size
            rc.drawLine(tail + size, ly, tail + (7f - 2f * outer) * size, ly, color)
        }
        rc.reset()
    }

    /** Flashing chevrons at the right edge, on the speeder's lane, while it is still off screen. */
    private fun drawSpeederWarnings(rc: RetroCanvas, engine: GameEngine, animMs: Float) {
        if ((animMs / BLINK_MS).toInt() % 2 != 0) return
        val edge = rc.halfWidthUnits
        for (i in engine.enemies.indices) {
            val e = engine.enemies[i]
            if (e.kind != EnemyKind.SPEEDER) continue
            val size = 10f - e.y / 9f
            if (e.x - 2.5f * size < edge) continue
            val s = 0.55f * size
            val tip = edge - 4f - 2.2f * s
            rc.reset()
            rc.lineWidth = 1.4f
            rc.drawPolyline(false, tip + s, e.y + s, tip, e.y, tip + s, e.y - s, color = WARNING_COLOR)
            rc.drawPolyline(false, tip + 2.2f * s, e.y + s, tip + 1.2f * s, e.y, tip + 2.2f * s, e.y - s, color = WARNING_COLOR)
        }
        rc.reset()
    }

    // ---- Coins & power-ups -----------------------------------------------------------------

    private fun drawCoin(rc: RetroCanvas, c: Coin, animMs: Float) {
        rc.reset()
        // Depth-scaled with the same formula as the pickup hitbox in GameEngine (10 - y/9), so a
        // coin near the horizon is small next to distant cars and grows as it approaches.
        val base = 10f - c.y / 9f
        if (!c.popping) {
            drawTokenShadow(rc, c.x, c.y, base)
            rc.alpha = TOKEN_ALPHA
            TokenArt.drawCoin(rc, c.x, c.y, base, TokenArt.spinAt(animMs), TOKEN_LINE)
        } else {
            // Original pop-out: each tick the coin grows by 2n while n (= pop) fades 1 -> 0, so the
            // growth is the running sum of 2n over the ticks elapsed so far.
            val k = (1f - c.pop) / GameConfig.COIN_POP_STEP
            val grown = base + 2f * (k - GameConfig.COIN_POP_STEP * k * (k + 1f) / 2f)
            rc.alpha = c.pop.coerceIn(0f, 1f)
            TokenArt.drawCoin(rc, c.x, c.y, grown, TokenArt.spinAt(animMs), TOKEN_LINE)
        }
        rc.reset()
    }

    private fun drawPowerUp(rc: RetroCanvas, p: PowerUp, animMs: Float) {
        rc.reset()
        val size = 10f - p.y / 9f
        drawTokenShadow(rc, p.x, p.y, size)
        rc.alpha = TOKEN_ALPHA
        TokenArt.drawPowerUp(rc, p.type, p.x, p.y, size, TokenArt.spinAt(animMs), TOKEN_LINE)
        rc.reset()
    }

    /** Soft contact shadow on the road, exactly as the original coin had it. */
    private fun drawTokenShadow(rc: RetroCanvas, x: Float, y: Float, size: Float) {
        rc.alpha = 0.2f
        rc.fillRound(x, y - size, 1.5f * size, 0.5f * size, 0xFF000000)
    }

    // ---- Fog -------------------------------------------------------------------------------

    /** Haze that thickens to the right: clear around the player, solid over the oncoming road. */
    private fun drawFog(rc: RetroCanvas, amount: Float) {
        val hw = rc.halfWidthUnits
        val right = hw + 20f
        val x0 = -0.05f * hw
        val x1 = 0.55f * hw
        rc.reset()
        rc.alpha = amount * 0.96f
        rc.fillHorizontalGradient((x0 + right) / 2f, -16f, right - x0, 156f, x0, x1, FOG_COLOR and 0x00FFFFFF, FOG_COLOR)
        rc.reset()
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
        const val TOKEN_ALPHA = 0.8f
        const val TOKEN_LINE = 0.8f
        const val BLINK_MS = 180f
        const val BLINKER_COLOR = 0xFFFFB020
        const val WARNING_COLOR = 0xFFFF3D3D
        const val FOG_COLOR = 0xFF2B2A3D
    }
}
