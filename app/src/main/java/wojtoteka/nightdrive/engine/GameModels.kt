package wojtoteka.nightdrive.engine

import wojtoteka.nightdrive.domain.PowerUpType

/** High-level phase of the simulation. Menu/shop/etc. navigation lives in the UI layer. */
enum class GamePhase { MENU, INTRO, PLAYING, GAME_OVER }

class Player {
    var x = 0f
    var y = GameConfig.PLAYER_START_Y
    var z = 0f
    var bounce = 0f
}

/**
 * Traffic variety. Plain cars are the original enemy; the others unlock as a run goes on:
 * trucks are long and slow, lane changers blink and drift into the next lane once on screen,
 * speeders come in very fast but are announced by a flashing marker at the right edge.
 */
enum class EnemyKind { CAR, TRUCK, LANE_CHANGER, SPEEDER }

class Enemy(
    var x: Float,
    var y: Float,
    var speed: Float,
    val hue: Int,
    val kind: EnemyKind = EnemyKind.CAR,
    /** Lane a [EnemyKind.LANE_CHANGER] drifts to; equals [y] for every other kind. */
    var targetY: Float = y,
) {
    /** Body length relative to a plain car; scales both the drawing and the hitbox. */
    val length: Float get() = if (kind == EnemyKind.TRUCK) GameConfig.TRUCK_LENGTH else 1f
    val changingLane: Boolean get() = kind == EnemyKind.LANE_CHANGER && y != targetY

    var collided = false
    /** Was inside the near-miss band around the player at some point (see GameConfig.NEAR_MISS_PAD_X). */
    var grazing = false
    /** Near-miss already paid out for this car - each car counts once. */
    var nearMissed = false
}

class Coin(
    var x: Float,
    var y: Float,
    val speed: Float,
) {
    var popping = false
    var pop = 1f          // 1 -> 0 pop animation once collected
    val scored: Boolean get() = popping && pop <= GameConfig.COIN_POP_STEP
}

class PowerUp(
    var x: Float,
    var y: Float,
    val type: PowerUpType,
) {
    var collected = false
}

/** Parallax background star. */
class Star(var x: Float, var y: Float)

/** Lightweight particle for crash debris and coin sparkles (pure polish, no gameplay effect). */
class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    val maxLife: Float,
    val color: Long,
    val size: Float,
)

/** One-shot things the UI/audio layer reacts to after each simulation step. */
sealed interface GameEvent {
    data object CoinCollected : GameEvent
    data class PowerUpCollected(val type: PowerUpType) : GameEvent
    data object ShieldBlocked : GameEvent
    data class NearMiss(val points: Int) : GameEvent
    data object Crash : GameEvent
    data object NewHighScore : GameEvent
    data class AchievementUnlocked(val id: String) : GameEvent
}
