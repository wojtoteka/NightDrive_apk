package wojtoteka.nightdrive.engine

/**
 * Central balance table. Every value is lifted directly from the original microScript build so
 * the moment-to-moment feel is identical; only the surrounding architecture changed.
 *
 * Coordinate space matches the original engine: origin at the screen centre, y-axis pointing UP,
 * with a fixed virtual height of [WORLD_HEIGHT] (y in [-100, 100]). The virtual width depends on
 * the device aspect ratio and is supplied at runtime as [GameState.halfWidth] * 2.
 *
 * The simulation advances at a fixed [TICK_HZ] so the per-frame increments below behave exactly as
 * they did at 60fps, while remaining independent of the display refresh rate.
 */
object GameConfig {
    const val TICK_HZ = 60
    const val TICK_MS = 1000f / TICK_HZ          // ~16.667 ms of virtual game time per step

    const val WORLD_HEIGHT = 200f                // smaller dimension maps to 200 units
    const val HALF_HEIGHT = WORLD_HEIGHT / 2f

    // --- Road geometry (virtual units) ---
    const val ROAD_TOP_Y = 20f                   // horizon line
    const val ROAD_BOTTOM_Y = -90f               // nearest edge

    // --- Player ---
    const val PLAYER_START_Y = -50f
    const val PLAYER_HITBOX = 20f
    const val PLAYER_SPEED = 1.8f
    const val PLAYER_Y_MAX = 25f
    const val PLAYER_Y_MIN = -52f
    const val PLAYER_X_LIMIT = 240f
    const val PLAYER_START_X_MARGIN = 70f        // starts this far left of the screen edge
    const val DESPAWN_MARGIN = 90f

    // --- Enemies ---
    const val ENEMY_HITBOX = 20f
    const val ENEMY_SPAWN_X = 500f
    const val ENEMY_START_DELAY_MS = 300f
    const val MAX_ENEMIES = 8
    const val ENEMY_SPAWN_MIN_MS = 150f

    // --- Traffic kinds (see EnemyKind). Unlock times are seconds into the run; chances are per
    // spawn once unlocked. Speeders spawn further out so their edge marker gives time to react.
    const val TRUCK_UNLOCK_S = 20f
    const val TRUCK_CHANCE = 0.18f
    const val TRUCK_SPEED = 0.7f
    const val TRUCK_LENGTH = 1.8f
    const val LANE_CHANGER_UNLOCK_S = 40f
    const val LANE_CHANGER_CHANCE = 0.15f
    const val LANE_CHANGE_TRIGGER = 0.55f        // starts drifting once x < halfWidth * this
    const val LANE_CHANGE_SPEED = 0.35f          // units per tick
    const val LANE_CHANGE_MIN = 18f
    const val LANE_CHANGE_MAX = 26f
    const val SPEEDER_UNLOCK_S = 60f
    const val SPEEDER_CHANCE = 0.10f
    const val SPEEDER_SPEED = 1.8f
    const val SPEEDER_SPAWN_X = 800f
    const val LANE_Y_MIN = -50f
    const val LANE_Y_MAX = 25f

    // --- Stages: every minute of a run is a stage, and the world can switch theme between them ---
    const val STAGE_MS = 60_000L
    const val STAGE_FADE_MS = 3_000f             // cross-fade between two themes

    // --- Road events (see WaveKind): the lighter ones from the first minute, the hard ones later ---
    const val EVENTS_START_MS = 45_000L
    const val WAVES_START_MS = 120_000L          // the difficulty ramp tops out around here
    const val WAVE_GAP_MIN_MS = 30_000
    const val WAVE_GAP_MAX_MS = 45_000
    const val WAVE_BANNER_MS = 2_200L
    const val CONVOY_MS = 4_500L                 // regular traffic is held back this long
    const val CONVOY_SPAWN_X = 650f
    /** Convoy slots across the road; one is left out as the gap. Spaced so every gap is passable. */
    val CONVOY_LANES = floatArrayOf(-46f, -21f, 2f, 22f)
    const val FOG_MS = 12_000L
    const val FOG_FADE_MS = 1_500L
    const val POLICE_MS = 5_500L
    const val POLICE_CARS = 3
    const val POLICE_INTERVAL_MS = 1_500L
    const val POLICE_SPEED = 3f                  // units per tick, driving to the right
    const val POLICE_SPAWN_MARGIN = 230f         // starts this far past the left edge
    const val ROADWORKS_MS = 6_000L
    const val BARRIER_INTERVAL_MS = 800L
    const val BARRIER_SPAWN_X = 520f
    const val BARRIER_SPEED = 1f
    val ROADWORKS_UPPER_LANES = floatArrayOf(2f, 22f)
    val ROADWORKS_LOWER_LANES = floatArrayOf(-46f, -21f)
    const val ROADWORKS_TRAFFIC_MIN_MS = 1_100f  // regular traffic thins out while a lane is shut
    const val GOLD_MS = 9_000L
    const val GOLD_COIN_INTERVAL_MS = 380L
    const val GOLD_COIN_CAP = 30
    const val GOLD_TRAFFIC_MIN_MS = 1_400f

    // --- Coins ---
    const val COIN_SPAWN_X = 500f
    const val COIN_BASE_SPEED = 1f
    const val MAX_COINS = 6
    const val COIN_MAGNET_PULL = 28f             // divisor: closer/frame = distance / 28
    const val COIN_POP_STEP = 0.05f              // pop animation decrement per tick
    const val COIN_PATTERN_UNLOCK_S = 8f
    const val COIN_PATTERN_CHANCE = 0.22f
    const val COIN_PATTERN_LENGTH = 6
    const val COIN_PATTERN_SPACING = 34f
    const val COIN_ARC_HEIGHT = 20f
    const val COIN_ZIGZAG_HEIGHT = 12f

    // --- Power-ups ---
    const val POWERUP_SPAWN_X = 500f
    const val POWERUP_SPEED = 1.2f
    const val POWERUP_HITBOX = 12f
    const val MAX_POWERUPS = 2
    const val POWERUP_SPAWN_MIN_MS = 2000
    const val POWERUP_SPAWN_MAX_MS = 7000
    const val SHIELD_MAX = 5
    const val SHIELD_MAX_UPGRADED = 8  // with the permanent "shield capacity" shop upgrade

    // Starter boosts (shop consumables) run far longer than their road-pickup counterparts -
    // at pickup durations (5-12s) they could never repay their coin price.
    const val STARTER_BOOST_MS = 60_000L

    // --- Near miss ---
    // A car that passes through this band around the player's hitbox without touching it pays out
    // a near-miss bonus and keeps the combo alive, so riding close to traffic is worth the risk.
    const val NEAR_MISS_PAD_X = 6f
    const val NEAR_MISS_PAD_Y = 7f
    const val NEAR_MISS_POINTS = 1f
    const val NEAR_MISS_FLASH_MS = 900L          // how long the HUD shows the "close!" tag

    // --- Nitro: near misses fill it, then it burns through traffic ---
    const val NITRO_NEAR_MISSES = 6
    const val NITRO_MS = 2_500L
    const val NITRO_WORLD_SPEED = 1.7f
    const val NITRO_SMASH_POINTS = 1f

    // --- Daily route ghost ---
    const val GHOST_SAMPLE_MS = 200L
    const val GHOST_MAX_SAMPLES = 3_000          // ten minutes
    const val GHOST_SCALE = 8f                   // world units to the stored fixed-point shorts

    // --- Combo ---
    const val COMBO_TIMEOUT_MS = 3500L
    const val COMBO_T1 = 3   // x1.5
    const val COMBO_T2 = 6   // x2
    const val COMBO_T3 = 12  // x3
    const val COMBO_T4 = 20  // x4

    // --- Global time / difficulty ---
    const val GLOBAL_TIME_MAX = 80f
    const val GLOBAL_TIME_STEP = 0.01f
    const val SPEED_MULT_MAX = 2.5f

    // --- Daily ---
    const val DAY_MS = 86_400_000L

    /** League week of a UTC day number; weeks start on Monday, same formula as the ranking server. */
    fun weekOf(dayKey: Long): Long = (dayKey + 3L) / 7L

    /** 0 below the first tier, then 1..4 for x1.5 / x2 / x3 / x4. */
    fun comboTier(streak: Int): Int = when {
        streak >= COMBO_T4 -> 4
        streak >= COMBO_T3 -> 3
        streak >= COMBO_T2 -> 2
        streak >= COMBO_T1 -> 1
        else -> 0
    }

    fun comboMultiplier(streak: Int): Float = when {
        streak >= COMBO_T4 -> 4f
        streak >= COMBO_T3 -> 3f
        streak >= COMBO_T2 -> 2f
        streak >= COMBO_T1 -> 1.5f
        else -> 1f
    }
}
