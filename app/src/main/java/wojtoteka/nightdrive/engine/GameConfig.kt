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

    // --- Coins ---
    const val COIN_SPAWN_X = 500f
    const val COIN_BASE_SPEED = 1f
    const val MAX_COINS = 6
    const val COIN_MAGNET_PULL = 28f             // divisor: closer/frame = distance / 28
    const val COIN_POP_STEP = 0.05f              // pop animation decrement per tick

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

    // --- Revive (rewarded-ad continue) ---
    const val REVIVE_GRACE_MS = 2500L    // post-revive invulnerability window

    // --- Daily ---
    const val DAILY_REWARD = 25
    const val DAY_MS = 86_400_000L

    fun comboMultiplier(streak: Int): Float = when {
        streak >= COMBO_T4 -> 4f
        streak >= COMBO_T3 -> 3f
        streak >= COMBO_T2 -> 2f
        streak >= COMBO_T1 -> 1.5f
        else -> 1f
    }
}
