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

class Enemy(
    var x: Float,
    var y: Float,
    var speed: Float,
    val hue: Int,
) {
    var collided = false
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

/** A short-lived "picked up X" banner shown centre-screen. */
class PowerUpToast(val type: PowerUpType, val bornAtMs: Long)

/** One-shot things the UI/audio layer reacts to after each simulation step. */
sealed interface GameEvent {
    data object CoinCollected : GameEvent
    data class PowerUpCollected(val type: PowerUpType) : GameEvent
    data object ShieldBlocked : GameEvent
    data object Crash : GameEvent
    data object NewHighScore : GameEvent
    data class AchievementUnlocked(val id: String) : GameEvent
}
