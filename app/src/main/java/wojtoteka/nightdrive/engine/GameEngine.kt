package wojtoteka.nightdrive.engine

import wojtoteka.nightdrive.domain.Achievement
import wojtoteka.nightdrive.domain.AchievementKind
import wojtoteka.nightdrive.domain.Achievements
import wojtoteka.nightdrive.domain.PlayerProfile
import wojtoteka.nightdrive.domain.PowerUpType
import wojtoteka.nightdrive.engine.GameConfig as C
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The full Night Drive simulation. Framework-agnostic and deterministic given an [Rng] seed, so it
 * can be unit-tested without Android. Advanced one fixed [C.TICK_MS] step at a time by [update].
 *
 * State is exposed as plain mutable fields for zero-allocation reads by the renderer and HUD.
 */
class GameEngine(
    val profile: PlayerProfile,
    private val rng: Rng = Rng(),
) {
    // Viewport (virtual units). Width depends on device aspect ratio.
    var halfWidth: Float = 222f
        private set

    // Phase
    var phase: GamePhase = GamePhase.MENU
        private set
    var paused: Boolean = false
        private set

    /** Debug-only: freezes the simulation on a staged scene (see [stageDemoScene]). */
    private var demoMode = false

    // Entities
    val player = Player()
    val enemies = ArrayList<Enemy>(16)
    val coins = ArrayList<Coin>(12)
    val powerups = ArrayList<PowerUp>(4)
    val stars = ArrayList<Star>(32)
    val particles = ArrayList<Particle>(64)

    // Run scalars
    var score = 0; private set
    var coinsInSession = 0; private set
    var streak = 0; private set
    var globalTime = 0f; private set
    var difficulty = 0; private set
    var gameOverSlide = 0f; private set   // b_y in the original: game-over panel slide-in
    var nearMissesInRun = 0; private set
    private var lastNearMissMs = 0L
    private var lastNearMissPoints = 0

    // Power-up runtime
    var magnetActive = false; private set
    var slowmoActive = false; private set
    var doubleActive = false; private set
    var coinRushActive = false; private set
    private var magnetUntil = 0L
    private var slowmoUntil = 0L
    private var doubleUntil = 0L
    private var coinRushUntil = 0L

    // Virtual clock (advances only while actively playing, so pauses freeze everything cleanly).
    // Accumulate the fractional 16.667ms tick so millisecond-based timers stay accurate.
    private var clockMs = 0L
    private var clockFrac = 0f
    private var enemyTimer = 0L
    private var coinTimer = 0L
    private var powerupTimer = 0L
    private var lastCoinMs = 0L
    private var streakStartMs = 0L
    private var enemySpawnDelay = C.ENEMY_START_DELAY_MS
    private var coinSpawnDelay = 0f
    private var powerupSpawnDelay = 0f
    private var bounce = 0f
    private var newHighAnnounced = false
    // Coin-earning starter boosts (magnet/double/coin rush) arm at run start but fire on the FIRST
    // collected coin - the opening seconds have no coins, so firing immediately would waste them.
    // The slow-motion starter is defensive, not coin-driven: it activates right at run start.
    private var starterBoostsPending = false
    // Base duration of whatever activated each timed effect (starter boosts run much longer than
    // road pickups), so the HUD bar drains against the right total.
    private var magnetDurMs = 1L
    private var slowmoDurMs = 1L
    private var doubleDurMs = 1L
    private var coinRushDurMs = 1L

    // Input intents (set each frame by the UI)
    private var inLeft = false
    private var inRight = false
    private var inUp = false
    private var inDown = false

    val events = ArrayList<GameEvent>(8)

    val survivalSeconds: Int get() = ((clockMs - streakStartMs) / 1000L).toInt().coerceAtLeast(0)
    val comboMultiplier: Float get() = C.comboMultiplier(streak)

    /** Points of the latest near-miss while its HUD tag is still showing, otherwise 0. */
    val nearMissFlash: Int
        get() = if (nearMissesInRun > 0 && clockMs - lastNearMissMs < C.NEAR_MISS_FLASH_MS) lastNearMissPoints else 0
    fun nowMs(): Long = clockMs

    init { buildStars() }

    // ---- Lifecycle -------------------------------------------------------------------------

    fun setViewport(halfWidthUnits: Float) {
        halfWidth = halfWidthUnits.coerceAtLeast(120f)
        if (phase == GamePhase.MENU || phase == GamePhase.INTRO) {
            player.x = -halfWidth - C.PLAYER_START_X_MARGIN
        }
    }

    fun enterMenu(realNowMs: Long) {
        phase = GamePhase.MENU
        paused = false
        enemies.clear(); coins.clear(); powerups.clear(); particles.clear()
        player.x = -halfWidth - C.PLAYER_START_X_MARGIN
        player.y = C.PLAYER_START_Y
        refreshDaily(realNowMs)
    }

    fun startRun(realNowMs: Long) {
        refreshDaily(realNowMs)
        enemies.clear(); coins.clear(); powerups.clear(); particles.clear()
        score = 0; coinsInSession = 0; streak = 0; globalTime = 0f; difficulty = 0
        magnetActive = false; slowmoActive = false; doubleActive = false; coinRushActive = false
        newHighAnnounced = false
        nearMissesInRun = 0; lastNearMissMs = 0L; lastNearMissPoints = 0
        clockMs = 0L; clockFrac = 0f; enemyTimer = 0L; coinTimer = 0L; powerupTimer = 0L; lastCoinMs = 0L
        enemySpawnDelay = C.ENEMY_START_DELAY_MS
        coinSpawnDelay = 0f
        powerupSpawnDelay = rng.intInRange(C.POWERUP_SPAWN_MIN_MS, C.POWERUP_SPAWN_MAX_MS).toFloat()
        player.x = -halfWidth - C.PLAYER_START_X_MARGIN
        player.y = C.PLAYER_START_Y
        player.z = 0f
        phase = GamePhase.INTRO
        starterBoostsPending = true
        // The slow-motion starter is an "easier warm-up": it covers the run from second zero.
        // The virtual clock is frozen during the drive-in intro, so none of it is wasted there.
        if (profile.pendingSlowmoStarts > 0) {
            profile.pendingSlowmoStarts--
            profile.lifetimeBoostsUsed++
            applyTimed(PowerUpType.SLOWMO, starterDuration(PowerUpType.SLOWMO))
        }
    }

    /** Applies one of each purchased coin-earning starter boost, if any are in stock. Called from
     *  [awardCoin] on the first coin of the run, not at run start - see [starterBoostsPending]. */
    private fun consumeStarterBoosts() {
        if (profile.pendingMagnetStarts > 0) { profile.pendingMagnetStarts--; profile.lifetimeBoostsUsed++; applyTimed(PowerUpType.MAGNET, starterDuration(PowerUpType.MAGNET)) }
        if (profile.pendingDoubleStarts > 0) { profile.pendingDoubleStarts--; profile.lifetimeBoostsUsed++; applyTimed(PowerUpType.DOUBLE, starterDuration(PowerUpType.DOUBLE)) }
        if (profile.pendingCoinRushStarts > 0) { profile.pendingCoinRushStarts--; profile.lifetimeBoostsUsed++; applyTimed(PowerUpType.COIN_RUSH, starterDuration(PowerUpType.COIN_RUSH)) }
    }

    fun togglePause() {
        if (phase == GamePhase.PLAYING) paused = !paused
    }

    fun setPaused(value: Boolean) {
        if (phase == GamePhase.PLAYING) paused = value
    }

    fun input(left: Boolean, right: Boolean, up: Boolean, down: Boolean) {
        inLeft = left; inRight = right; inUp = up; inDown = down
    }

    // ---- Main step -------------------------------------------------------------------------

    fun update(realNowMs: Long) {
        events.clear()
        bounce = 0.3f * sin(clockMs / 30f)
        updateStars()

        when (phase) {
            GamePhase.MENU -> refreshDaily(realNowMs)
            GamePhase.INTRO -> stepIntro()
            GamePhase.PLAYING -> if (!paused && !demoMode) stepPlaying()
            GamePhase.GAME_OVER -> if (gameOverSlide > 0f) gameOverSlide -= 2f
        }
        updateParticles()
    }

    private fun stepIntro() {
        if (player.x < -90f) {
            player.x += 2f
        } else {
            phase = GamePhase.PLAYING
            streakStartMs = clockMs
        }
    }

    private fun stepPlaying() {
        clockFrac += C.TICK_MS
        val whole = clockFrac.toLong()
        clockMs += whole
        clockFrac -= whole
        if (globalTime < C.GLOBAL_TIME_MAX) globalTime += C.GLOBAL_TIME_STEP

        updateComboTimeout()
        updatePowerupTimers()
        updatePlayer()

        updateEnemies()
        maybeSpawnEnemy()
        updateCoins()
        maybeSpawnCoin()
        updatePowerups()
        maybeSpawnPowerup()

        difficulty = (score / 20)

        if (score > profile.highScore) {
            profile.highScore = score
            if (!newHighAnnounced) { events += GameEvent.NewHighScore; newHighAnnounced = true }
        }
    }

    // ---- Player ----------------------------------------------------------------------------

    private fun updatePlayer() {
        val p = player
        if (inRight) {
            if (kotlin.math.abs(p.x - p.z) <= C.PLAYER_X_LIMIT) p.x += C.PLAYER_SPEED - p.z
        }
        if (inLeft) {
            if (kotlin.math.abs(p.x - p.z) <= C.PLAYER_X_LIMIT) p.x -= (C.PLAYER_SPEED - p.z / 2f) / 1.2f
        }
        if (inUp) {
            if (p.y <= C.PLAYER_Y_MAX) {
                p.y += C.PLAYER_SPEED / 2f
                if (kotlin.math.abs(p.x - p.z) <= C.PLAYER_X_LIMIT) p.x += -p.x / 110f
            }
        }
        if (inDown) {
            if (p.y >= C.PLAYER_Y_MIN) {
                p.y -= C.PLAYER_SPEED / 2f
                if (kotlin.math.abs(p.x - p.z) <= C.PLAYER_X_LIMIT) p.x -= -p.x / 110f
            }
        }
        p.z = p.y / 30f
        if (p.x - p.z < -C.PLAYER_X_LIMIT) p.x += 1f
        if (p.x - p.z > C.PLAYER_X_LIMIT) p.x -= 1f
        p.bounce = bounce
    }

    // ---- Enemies ---------------------------------------------------------------------------

    private fun updateEnemies() {
        val despawnX = -halfWidth - C.DESPAWN_MARGIN
        val factor = if (slowmoActive) 0.5f else 1f
        // Index loop (here and in the other entity updates): same forward order and removal
        // semantics as an iterator, without allocating one every 60Hz tick.
        var i = 0
        while (i < enemies.size) {
            val e = enemies[i]
            e.x -= (e.speed + globalTime / 55f) * factor
            if (e.changingLane && e.x < halfWidth * C.LANE_CHANGE_TRIGGER) {
                val step = C.LANE_CHANGE_SPEED * factor
                e.y = if (e.targetY > e.y) min(e.targetY, e.y + step) else max(e.targetY, e.y - step)
            }
            if (e.x <= despawnX) { enemies.removeAt(i); continue }
            if (enemyOverlaps(e, 0f, 0f)) {
                if (profile.shieldCharges > 0) {
                    profile.shieldCharges -= 1
                    profile.lifetimeShieldBlocks += 1
                    events += GameEvent.ShieldBlocked
                    enemies.removeAt(i)
                    continue
                } else {
                    enemies.removeAt(i)
                    triggerGameOver()
                    return
                }
            }
            // Near miss: the car entered the padded band around the player and left it again
            // without a collision (a collision removes the car above, so it never gets here).
            if (!e.nearMissed) {
                if (enemyOverlaps(e, C.NEAR_MISS_PAD_X, C.NEAR_MISS_PAD_Y)) {
                    e.grazing = true
                } else if (e.grazing) {
                    e.nearMissed = true
                    awardNearMiss(e)
                }
            }
            i++
        }
    }

    /**
     * The original per-depth collision box between [e] and the player, grown by [padX]/[padY] on
     * every side. With zero padding this is exactly the crash test; padded it is the near-miss band.
     */
    private fun enemyOverlaps(e: Enemy, padX: Float, padY: Float): Boolean {
        val ph = C.PLAYER_HITBOX
        val hb = C.ENEMY_HITBOX
        val px = player.x
        val py = player.y
        val y = e.y + bounce
        val x = e.x
        val size = 10f - y / 9f
        val len = e.length
        return when {
            y <= -20f ->
                x - hb * 2.3f * len <= px + ph + padX && x + hb * 2.3f * len - size >= px - ph - padX &&
                    y + hb / 4f - 10f >= py - ph / 4f - 9f - padY && y - hb / 4f - 10f <= py + ph / 4f - 9f + padY
            y <= 10f ->
                x - hb * 1.4f * len <= px + ph + padX && x + hb * 1.4f * len - size >= px - ph - padX &&
                    y + hb / 7f - 10f >= py - ph / 4f - 10f - padY && y - hb / 7f - 10f <= py + ph / 4f - 10f + padY
            else ->
                x - hb * len <= px + ph + padX && x + hb * len - size >= px - ph - padX &&
                    y + hb / 12f - 8f >= py - ph / 5f - 8f - padY && y - hb / 12f - 8f <= py + ph / 5f - 8f + padY
        }
    }

    private fun awardNearMiss(e: Enemy) {
        val value = ceil(C.NEAR_MISS_POINTS * comboMultiplier * (if (doubleActive) 2f else 1f)).toInt()
        score += value
        lastCoinMs = clockMs // a near miss keeps the combo timer alive, like a coin
        bumpStreak()
        nearMissesInRun += 1
        profile.lifetimeNearMisses += 1
        lastNearMissMs = clockMs
        lastNearMissPoints = value
        spawnNearMissSparks(e)
        events += GameEvent.NearMiss(value)
        checkAchievements()
    }

    private fun maybeSpawnEnemy() {
        if (clockMs - enemyTimer < enemySpawnDelay || enemies.size >= C.MAX_ENEMIES) return
        var speedMult = 1f + (score / 30) * 0.1f
        speedMult = min(speedMult, C.SPEED_MULT_MAX)
        val hue = rng.intInRange(0, 359)
        val y = rng.intInRange(25f, -50f).toFloat()
        // Always draw the same number of values per spawn, whatever kind comes out, so the
        // sequence of traffic stays reproducible from the seed.
        val kind = pickEnemyKind(rng.next())
        val shift = rng.nextFloat(C.LANE_CHANGE_MIN, C.LANE_CHANGE_MAX)
        val up = rng.next() < 0.5f
        enemies += when (kind) {
            EnemyKind.CAR -> Enemy(C.ENEMY_SPAWN_X, y, speedMult, hue)
            EnemyKind.TRUCK -> Enemy(C.ENEMY_SPAWN_X, y, speedMult * C.TRUCK_SPEED, hue, kind)
            EnemyKind.SPEEDER -> Enemy(C.SPEEDER_SPAWN_X, y, speedMult * C.SPEEDER_SPEED, hue, kind)
            EnemyKind.LANE_CHANGER -> {
                var target = if (up) y + shift else y - shift
                if (target > C.LANE_Y_MAX || target < C.LANE_Y_MIN) target = if (up) y - shift else y + shift
                Enemy(C.ENEMY_SPAWN_X, y, speedMult, hue, kind, target.coerceIn(C.LANE_Y_MIN, C.LANE_Y_MAX))
            }
        }
        enemyTimer = clockMs
        val lo = 870f - ceil(globalTime * 2f) - difficulty * 10f
        val hi = 3900f - ceil(globalTime * 25f) - difficulty * 20f
        enemySpawnDelay = max(C.ENEMY_SPAWN_MIN_MS, rng.intInRange(lo, hi).toFloat())
    }

    /** Which kind of traffic a spawn becomes, given a uniform [roll] in [0, 1). */
    private fun pickEnemyKind(roll: Float): EnemyKind {
        val runSec = (clockMs - streakStartMs) / 1000f
        var acc = 0f
        if (runSec >= C.SPEEDER_UNLOCK_S) { acc += C.SPEEDER_CHANCE; if (roll < acc) return EnemyKind.SPEEDER }
        if (runSec >= C.LANE_CHANGER_UNLOCK_S) { acc += C.LANE_CHANGER_CHANCE; if (roll < acc) return EnemyKind.LANE_CHANGER }
        if (runSec >= C.TRUCK_UNLOCK_S) { acc += C.TRUCK_CHANCE; if (roll < acc) return EnemyKind.TRUCK }
        return EnemyKind.CAR
    }

    // ---- Coins -----------------------------------------------------------------------------

    private fun updateCoins() {
        val despawnX = -halfWidth - C.DESPAWN_MARGIN
        var i = 0
        while (i < coins.size) {
            val c = coins[i]
            if (!c.popping) {
                c.x -= c.speed + globalTime / 55f
                if (magnetActive) {
                    c.x += (player.x - c.x) / C.COIN_MAGNET_PULL
                    c.y += (player.y - c.y) / C.COIN_MAGNET_PULL
                }
                if (coinHitsPlayer(c)) c.popping = true
            } else {
                c.pop -= C.COIN_POP_STEP
            }
            if (c.x <= despawnX) { coins.removeAt(i); continue }
            if (c.scored) { awardCoin(); coins.removeAt(i); continue }
            i++
        }
    }

    private fun coinHitsPlayer(c: Coin): Boolean {
        val ph = C.PLAYER_HITBOX
        val size = 10f - c.y / 9f
        val hb = 5f + 0.5f * size
        return c.x - hb <= player.x + ph && c.x + hb >= player.x - ph &&
            c.y + hb >= player.y - ph / 4f - 10f && c.y - hb <= player.y + ph / 4f - 10f
    }

    private fun awardCoin() {
        // First coin of the run fires any purchased starter boosts, so this very coin already
        // benefits from double points / coin rush. Unused stock survives a coinless run.
        if (starterBoostsPending) {
            starterBoostsPending = false
            consumeStarterBoosts()
        }
        val value = ceil(1f * comboMultiplier * (if (doubleActive) 2f else 1f)).toInt()
        score += value
        coinsInSession += 1
        lastCoinMs = clockMs
        val coinGain = if (coinRushActive) 2 else 1
        profile.totalCoins += coinGain
        profile.lifetimeCoins += coinGain
        bumpStreak()
        spawnCoinSparkle()
        events += GameEvent.CoinCollected
        if (!profile.dailyCompleted && profile.dailyType == 0) {
            profile.dailyProgress += 1
            if (profile.dailyProgress >= profile.dailyGoal) completeDaily()
        }
        checkAchievements()
    }

    /** One more link in the combo chain (a coin or a near miss). */
    private fun bumpStreak() {
        streak += 1
        if (streak > profile.longestStreak) profile.longestStreak = streak
        // Combo daily tracks the best streak reached today (across runs).
        if (!profile.dailyCompleted && profile.dailyType == 2) {
            if (streak > profile.dailyProgress) profile.dailyProgress = streak
            if (profile.dailyProgress >= profile.dailyGoal) completeDaily()
        }
    }

    private fun maybeSpawnCoin() {
        if (clockMs - coinTimer < coinSpawnDelay || coins.size >= C.MAX_COINS) return
        coins += Coin(C.COIN_SPAWN_X, rng.intInRange(21f, -60f).toFloat(), C.COIN_BASE_SPEED)
        coinTimer = clockMs
        val lo = 720f - ceil(globalTime * 2f)
        val hi = 5000f - ceil(globalTime * 45f)
        coinSpawnDelay = rng.intInRange(lo, hi).toFloat()
    }

    // ---- Power-ups -------------------------------------------------------------------------

    private fun updatePowerups() {
        val despawnX = -halfWidth - C.DESPAWN_MARGIN
        var i = 0
        while (i < powerups.size) {
            val p = powerups[i]
            if (!p.collected) {
                p.x -= C.POWERUP_SPEED + globalTime / 70f
                if (powerupHitsPlayer(p)) {
                    p.collected = true
                    applyPowerup(p.type)
                    profile.lifetimePowerups += 1
                    if (!profile.dailyCompleted && profile.dailyType == 3) {
                        profile.dailyProgress += 1
                        if (profile.dailyProgress >= profile.dailyGoal) completeDaily()
                    }
                    checkAchievements()
                    events += GameEvent.PowerUpCollected(p.type)
                }
            }
            if (p.x <= despawnX || p.collected) { powerups.removeAt(i); continue }
            i++
        }
    }

    private fun powerupHitsPlayer(p: PowerUp): Boolean {
        val ph = C.PLAYER_HITBOX
        val hb = C.POWERUP_HITBOX
        return p.x - hb <= player.x + ph && p.x + hb >= player.x - ph &&
            p.y + hb >= player.y - ph / 4f - 10f && p.y - hb <= player.y + ph / 4f - 10f
    }

    /** Effective duration of a timed road pickup, extended by the tiered shop upgrades. */
    private fun boostedDuration(type: PowerUpType): Long =
        type.durationMs * (100 + profile.boostDurationPercent(type)) / 100

    /** Effective duration of a starter boost - the long base, same tier upgrades applied. */
    private fun starterDuration(type: PowerUpType): Long =
        C.STARTER_BOOST_MS * (100 + profile.boostDurationPercent(type)) / 100

    private fun applyPowerup(type: PowerUpType) = applyTimed(type, boostedDuration(type))

    private fun applyTimed(type: PowerUpType, durationMs: Long) {
        // Never shorten a running effect: a road pickup grabbed while a longer starter boost is
        // active keeps the later deadline (with equal durations this is the old reset behaviour).
        when (type) {
            PowerUpType.SHIELD -> profile.shieldCharges = min(profile.shieldCharges + 1, profile.shieldMax)
            PowerUpType.MAGNET -> {
                val until = clockMs + durationMs
                if (!magnetActive || until > magnetUntil) { magnetUntil = until; magnetDurMs = durationMs }
                magnetActive = true
            }
            PowerUpType.SLOWMO -> {
                val until = clockMs + durationMs
                if (!slowmoActive || until > slowmoUntil) { slowmoUntil = until; slowmoDurMs = durationMs }
                slowmoActive = true
            }
            PowerUpType.DOUBLE -> {
                val until = clockMs + durationMs
                if (!doubleActive || until > doubleUntil) { doubleUntil = until; doubleDurMs = durationMs }
                doubleActive = true
            }
            PowerUpType.COIN_RUSH -> {
                val until = clockMs + durationMs
                if (!coinRushActive || until > coinRushUntil) { coinRushUntil = until; coinRushDurMs = durationMs }
                coinRushActive = true
            }
        }
    }

    private fun updatePowerupTimers() {
        if (magnetActive && clockMs > magnetUntil) magnetActive = false
        if (slowmoActive && clockMs > slowmoUntil) slowmoActive = false
        if (doubleActive && clockMs > doubleUntil) doubleActive = false
        if (coinRushActive && clockMs > coinRushUntil) coinRushActive = false
    }

    /** Remaining fraction [0,1] for an active timed power-up (for HUD bars). */
    fun powerupRemainingFraction(type: PowerUpType): Float = when (type) {
        PowerUpType.MAGNET -> if (magnetActive) frac(magnetUntil, magnetDurMs) else 0f
        PowerUpType.SLOWMO -> if (slowmoActive) frac(slowmoUntil, slowmoDurMs) else 0f
        PowerUpType.DOUBLE -> if (doubleActive) frac(doubleUntil, doubleDurMs) else 0f
        PowerUpType.COIN_RUSH -> if (coinRushActive) frac(coinRushUntil, coinRushDurMs) else 0f
        PowerUpType.SHIELD -> 0f
    }

    fun powerupRemainingSeconds(type: PowerUpType): Int {
        val until = when (type) {
            PowerUpType.MAGNET -> magnetUntil
            PowerUpType.SLOWMO -> slowmoUntil
            PowerUpType.DOUBLE -> doubleUntil
            PowerUpType.COIN_RUSH -> coinRushUntil
            PowerUpType.SHIELD -> return profile.shieldCharges
        }
        return ceil(max(0L, until - clockMs) / 1000f).toInt()
    }

    private fun frac(until: Long, duration: Long): Float =
        (max(0L, until - clockMs).toFloat() / duration).coerceIn(0f, 1f)

    private fun maybeSpawnPowerup() {
        if (clockMs - powerupTimer < powerupSpawnDelay || powerups.size >= C.MAX_POWERUPS) return
        val type = rng.pick(PowerUpType.spawnable)
        powerups += PowerUp(C.POWERUP_SPAWN_X, rng.intInRange(25f, -50f).toFloat(), type)
        powerupTimer = clockMs
        powerupSpawnDelay = rng.intInRange(C.POWERUP_SPAWN_MIN_MS, C.POWERUP_SPAWN_MAX_MS).toFloat()
    }

    // ---- Combo -----------------------------------------------------------------------------

    private fun updateComboTimeout() {
        if (streak > 0 && clockMs - lastCoinMs > C.COMBO_TIMEOUT_MS) streak = 0
    }

    // ---- Game over -------------------------------------------------------------------------

    private fun triggerGameOver() {
        val survived = survivalSeconds
        if (survived > profile.longestStreakTimeSec) profile.longestStreakTimeSec = survived
        streak = 0
        if (!profile.dailyCompleted && profile.dailyType == 1) {
            if (survived > profile.dailyProgress) profile.dailyProgress = survived
            if (profile.dailyProgress >= profile.dailyGoal) completeDaily()
        }
        checkAchievements()
        spawnCrashParticles()
        gameOverSlide = 370f
        phase = GamePhase.GAME_OVER
        events += GameEvent.Crash
    }

    /**
     * Debug-only helper used to produce marketing screenshots: arranges a lively, frozen scene
     * (score, combo, traffic, coins, an amulet) that the renderer draws exactly as real gameplay.
     * Never called from release code paths.
     */
    fun stageDemoScene() {
        phase = GamePhase.PLAYING
        paused = false
        demoMode = true
        clockMs = 60_000L
        streakStartMs = clockMs - 42_000L
        score = 34
        coinsInSession = 15
        streak = 8
        lastCoinMs = clockMs
        globalTime = 20f
        doubleActive = true
        doubleUntil = clockMs + 7_000L
        magnetActive = false
        slowmoActive = false
        player.x = -70f
        player.y = -28f
        player.z = player.y / 30f
        enemies.clear(); coins.clear(); powerups.clear(); particles.clear()
        enemies += Enemy(78f, -44f, 1f, 0)      // red, near lane
        enemies += Enemy(150f, 0f, 1f, 205)     // blue, mid-right
        enemies += Enemy(-38f, 14f, 1f, 130)    // green, far
        enemies += Enemy(52f, 21f, 1f, 285)     // violet silhouette by the sun
        coins += Coin(-42f, -26f, 1f)
        coins += Coin(-6f, -30f, 1f)
        coins += Coin(30f, -34f, 1f)
        coins += Coin(112f, -6f, 1f)
        powerups += PowerUp(132f, -55f, PowerUpType.MAGNET)
    }

    // ---- Daily -----------------------------------------------------------------------------

    fun refreshDaily(realNowMs: Long) {
        val today = realNowMs / C.DAY_MS
        val p = profile
        if (p.dailyDayKey == today) return
        val prev = p.dailyDayKey
        if (prev != Long.MIN_VALUE && today - prev == 1L) p.dailyStreak += 1
        else p.dailyStreak = 1
        if (p.dailyStreak > p.bestDailyStreak) p.bestDailyStreak = p.dailyStreak
        p.dailyDayKey = today
        p.dailyCompleted = false
        p.dailyProgress = 0
        // 0 = collect coins, 1 = survive seconds, 2 = hit a combo in one run, 3 = collect power-ups
        p.dailyType = rng.intInRange(0, 3)
        p.dailyGoal = when (p.dailyType) {
            2 -> rng.intInRange(8, 16)
            3 -> rng.intInRange(3, 6)
            else -> rng.intInRange(25, 60)
        }
    }

    private fun completeDaily() {
        profile.dailyCompleted = true
        profile.totalCoins += C.DAILY_REWARD
    }

    // ---- Achievements ----------------------------------------------------------------------

    private fun checkAchievements() {
        val survived = survivalSeconds
        for (a in Achievements.all) {
            if (profile.unlockedAchievements.contains(a.id)) continue
            if (meetsGoal(a, survived)) {
                profile.unlockedAchievements.add(a.id)
                profile.totalCoins += a.reward
                events += GameEvent.AchievementUnlocked(a.id)
            }
        }
    }

    private fun meetsGoal(a: Achievement, survived: Int): Boolean = when (a.kind) {
        AchievementKind.TOTAL_COINS -> profile.lifetimeCoins >= a.goal
        AchievementKind.SCORE -> score >= a.goal
        AchievementKind.STREAK -> streak >= a.goal
        AchievementKind.TIME -> survived >= a.goal
        AchievementKind.DAILY_STREAK -> profile.bestDailyStreak >= a.goal
        AchievementKind.SKIN_COUNT -> profile.ownedSkins.size >= a.goal
        AchievementKind.SHIELD_BLOCK -> profile.lifetimeShieldBlocks >= a.goal
        AchievementKind.BOOST_USED -> profile.lifetimeBoostsUsed >= a.goal
        AchievementKind.POWERUP_COLLECT -> profile.lifetimePowerups >= a.goal
        AchievementKind.NEAR_MISS -> profile.lifetimeNearMisses >= a.goal
    }

    // ---- Particles & stars -----------------------------------------------------------------

    private fun spawnCoinSparkle() {
        repeat(6) {
            particles += Particle(
                x = player.x, y = player.y + 6f,
                vx = rng.nextFloat(-1.4f, 1.4f), vy = rng.nextFloat(0.4f, 2.0f),
                life = 1f, maxLife = 1f, color = 0xFFFFD54A, size = rng.nextFloat(0.8f, 1.8f),
            )
        }
    }

    /** Cyan sparks streaking off between the player and the car that just slipped past. */
    private fun spawnNearMissSparks(e: Enemy) {
        val mx = (player.x + e.x) / 2f
        val my = (player.y + e.y) / 2f
        repeat(8) {
            particles += Particle(
                x = mx, y = my,
                vx = rng.nextFloat(-2.6f, -0.6f), vy = rng.nextFloat(-0.8f, 1.6f),
                life = 1f, maxLife = 1f, color = 0xFF37E7FF, size = rng.nextFloat(0.8f, 1.6f),
            )
        }
    }

    private fun spawnCrashParticles() {
        repeat(28) {
            particles += Particle(
                x = player.x, y = player.y + 4f,
                vx = rng.nextFloat(-4f, 4f), vy = rng.nextFloat(-1f, 5f),
                life = 1f, maxLife = 1f,
                color = if (it % 2 == 0) 0xFFFF3DAE else 0xFFFF8A3D,
                size = rng.nextFloat(1.2f, 3.4f),
            )
        }
    }

    private fun updateParticles() {
        var i = 0
        while (i < particles.size) {
            val p = particles[i]
            p.x += p.vx
            p.y += p.vy
            p.vy -= 0.12f
            p.life -= 0.04f
            if (p.life <= 0f) { particles.removeAt(i); continue }
            i++
        }
    }

    private fun buildStars() {
        stars.clear()
        repeat(28) {
            stars += Star(rng.nextFloat(-halfWidth, halfWidth + 60f), rng.intInRange(18f, 100f).toFloat())
        }
    }

    private fun updateStars() {
        if (phase == GamePhase.GAME_OVER) return
        for (i in stars.indices) {
            val s = stars[i]
            s.x -= 1f
            if (s.x <= -halfWidth) {
                s.x = rng.nextFloat(60f, halfWidth + 80f)
                s.y = rng.intInRange(18f, 100f).toFloat()
            }
        }
    }
}
