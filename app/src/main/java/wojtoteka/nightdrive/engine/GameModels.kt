package wojtoteka.nightdrive.engine

import wojtoteka.nightdrive.domain.MissionType
import wojtoteka.nightdrive.domain.PowerUpType

/**
 * STANDARD is the normal endless run. DAILY is the daily route: the same seed for every player on
 * a given UTC day, and equal chances - no garage shield, no starter boosts, no duration upgrades.
 */
enum class RunMode { STANDARD, DAILY }

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

/**
 * Late-game events that start after [GameConfig.WAVES_START_MS] of a run: a convoy is a wall of
 * cars across the road with one lane left open, fog hides the right part of the road so traffic
 * shows up late.
 */
enum class WaveKind { CONVOY, FOG }

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
    data class WaveStarted(val kind: WaveKind) : GameEvent
    /** A new stage of the run began; [stage] is 1 for the second minute, 2 for the third... */
    data class StageReached(val stage: Int) : GameEvent
    data object Crash : GameEvent
    data object NewHighScore : GameEvent
    data class AchievementUnlocked(val id: String) : GameEvent
    data class MissionCompleted(val type: MissionType, val goal: Int, val reward: Int) : GameEvent
    data class AllMissionsCompleted(val bonus: Int) : GameEvent
}
