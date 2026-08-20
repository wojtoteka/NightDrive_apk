package wojtoteka.nightdrive.ui

import androidx.compose.runtime.Immutable
import wojtoteka.nightdrive.domain.Language
import wojtoteka.nightdrive.domain.PowerUpType
import wojtoteka.nightdrive.engine.GamePhase

/** Top-level screen the player is looking at. The animated world is drawn behind all of them. */
enum class Destination { MENU, GAME, SHOP, ACHIEVEMENTS, SETTINGS, LEADERBOARD }

/** One of the four on-screen d-pad directions. */
enum class PadDir { LEFT, RIGHT, UP, DOWN }

/** Immutable snapshot of persistent data for the menu/shop/achievements/settings screens. */
@Immutable
data class ProfileSnapshot(
    val highScore: Int = 0,
    val totalCoins: Int = 0,
    val lifetimeCoins: Int = 0,
    val bestTimeSec: Int = 0,
    val longestStreak: Int = 0,
    val shieldCharges: Int = 0,
    val shieldMax: Int = 5,
    val shieldCapacityUpgrade: Boolean = false,
    val boostDurationLevelAll: Int = 0,
    val boostDurationLevelMagnet: Int = 0,
    val boostDurationLevelSlowmo: Int = 0,
    val boostDurationLevelDouble: Int = 0,
    val boostDurationLevelCoinRush: Int = 0,
    val pendingMagnetStarts: Int = 0,
    val pendingSlowmoStarts: Int = 0,
    val pendingDoubleStarts: Int = 0,
    val pendingCoinRushStarts: Int = 0,
    val lifetimeShieldBlocks: Int = 0,
    val lifetimeBoostsUsed: Int = 0,
    val lifetimePowerups: Int = 0,
    val dailyType: Int = 0,
    val dailyGoal: Int = 30,
    val dailyProgress: Int = 0,
    val dailyCompleted: Boolean = false,
    val dailyStreak: Int = 0,
    val bestDailyStreak: Int = 0,
    val ownedSkins: Set<String> = setOf("classic"),
    val selectedSkin: String = "classic",
    val ownedThemes: Set<String> = setOf("sunset"),
    val selectedTheme: String = "sunset",
    val unlockedAchievements: Set<String> = emptySet(),
    val musicEnabled: Boolean = true,
    val sfxEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val language: Language = Language.SYSTEM,
)

@Immutable
data class HudPowerUp(
    val type: PowerUpType,
    val isCharge: Boolean,
    val chargeCount: Int,
    val remainingSeconds: Int,
    val fraction: Float,
)

/** Per-frame HUD data. Structural equality means unchanged frames cause no recomposition. */
@Immutable
data class HudState(
    val phase: GamePhase = GamePhase.MENU,
    val paused: Boolean = false,
    val score: Int = 0,
    val coinsInSession: Int = 0,
    val comboMultiplier: Float = 1f,
    val streak: Int = 0,
    val powerups: List<HudPowerUp> = emptyList(),
    val dailyType: Int = 0,
    val dailyGoal: Int = 30,
    val dailyProgress: Int = 0,
    val dailyCompleted: Boolean = false,
    val highScore: Int = 0,
)
