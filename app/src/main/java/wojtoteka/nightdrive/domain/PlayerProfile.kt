package wojtoteka.nightdrive.domain

import wojtoteka.nightdrive.engine.GameConfig

/**
 * All persistent player data. A single mutable holder that the game engine reads and
 * mutates during play (mirroring the original localStorage-backed globals) and that the
 * data layer serialises to DataStore. Keeping it in one place makes saving/loading trivial
 * and keeps the engine free of any Android dependency.
 */
class PlayerProfile {
    // Progression
    var highScore: Int = 0
    var totalCoins: Int = 0            // soft currency, spent in the shop
    var lifetimeCoins: Int = 0         // never decreases; tracks the "collect N coins" achievement
    var longestStreak: Int = 0
    var longestStreakTimeSec: Int = 0
    var shieldCharges: Int = 0         // carried between runs, max 5

    // Lifetime counters feeding the SHIELD_BLOCK / BOOST_USED / POWERUP_COLLECT / NEAR_MISS achievements.
    var lifetimeShieldBlocks: Int = 0
    var lifetimeBoostsUsed: Int = 0
    var lifetimePowerups: Int = 0
    var lifetimeNearMisses: Int = 0

    // Starter boosts bought in the shop: stock of one-shot activations consumed at the start
    // of the next run (one of each kind per run), on top of the normal in-run random spawns.
    var pendingMagnetStarts: Int = 0
    var pendingSlowmoStarts: Int = 0
    var pendingDoubleStarts: Int = 0
    var pendingCoinRushStarts: Int = 0

    // Permanent, one-time shop upgrades.
    var shieldCapacityUpgrade: Boolean = false   // raises the shield charge cap
    val shieldMax: Int get() = if (shieldCapacityUpgrade) GameConfig.SHIELD_MAX_UPGRADED else GameConfig.SHIELD_MAX

    // Tiered duration upgrades (levels 0..3 = +0/+20/+50/+100%); the "all" ladder stacks with
    // each power-up's own ladder. See [BoostDurationUpgrade].
    var boostDurationLevelAll: Int = 0
    var boostDurationLevelMagnet: Int = 0
    var boostDurationLevelSlowmo: Int = 0
    var boostDurationLevelDouble: Int = 0
    var boostDurationLevelCoinRush: Int = 0

    fun boostDurationLevel(type: PowerUpType): Int = when (type) {
        PowerUpType.MAGNET -> boostDurationLevelMagnet
        PowerUpType.SLOWMO -> boostDurationLevelSlowmo
        PowerUpType.DOUBLE -> boostDurationLevelDouble
        PowerUpType.COIN_RUSH -> boostDurationLevelCoinRush
        PowerUpType.SHIELD -> 0
    }

    /** Combined duration bonus for [type] in percent (all-ladder + individual ladder). */
    fun boostDurationPercent(type: PowerUpType): Int =
        BoostDurationUpgrade.percent(boostDurationLevelAll) + BoostDurationUpgrade.percent(boostDurationLevel(type))

    // Cosmetics
    var ownedSkins: MutableSet<String> = mutableSetOf(Skins.DEFAULT_ID)
    var selectedSkin: String = Skins.DEFAULT_ID
    var ownedThemes: MutableSet<String> = mutableSetOf(WorldThemes.DEFAULT_ID)
    var selectedTheme: String = WorldThemes.DEFAULT_ID

    // Achievements (ids of unlocked ones)
    var unlockedAchievements: MutableSet<String> = mutableSetOf()

    // Daily challenge
    var dailyDayKey: Long = Long.MIN_VALUE
    var dailyStreak: Int = 0
    var bestDailyStreak: Int = 0
    var dailyCompleted: Boolean = false
    var dailyProgress: Int = 0
    var dailyGoal: Int = 30
    var dailyType: Int = 0             // 0 = collect coins, 1 = survive seconds

    // Settings
    var sceneryRotation: Boolean = true   // cycle owned world themes stage by stage during a run
    var musicEnabled: Boolean = true
    var sfxEnabled: Boolean = true
    var hapticsEnabled: Boolean = true
    var language: String = Language.SYSTEM.code

    // Last nickname used for the online leaderboard (pre-fills the entry field)
    var playerName: String = ""

    fun ownsSkin(id: String): Boolean = ownedSkins.contains(id)

    fun copyFrom(other: PlayerProfile) {
        highScore = other.highScore
        totalCoins = other.totalCoins
        lifetimeCoins = other.lifetimeCoins
        longestStreak = other.longestStreak
        longestStreakTimeSec = other.longestStreakTimeSec
        shieldCharges = other.shieldCharges
        pendingMagnetStarts = other.pendingMagnetStarts
        pendingSlowmoStarts = other.pendingSlowmoStarts
        pendingDoubleStarts = other.pendingDoubleStarts
        pendingCoinRushStarts = other.pendingCoinRushStarts
        shieldCapacityUpgrade = other.shieldCapacityUpgrade
        boostDurationLevelAll = other.boostDurationLevelAll
        boostDurationLevelMagnet = other.boostDurationLevelMagnet
        boostDurationLevelSlowmo = other.boostDurationLevelSlowmo
        boostDurationLevelDouble = other.boostDurationLevelDouble
        boostDurationLevelCoinRush = other.boostDurationLevelCoinRush
        lifetimeShieldBlocks = other.lifetimeShieldBlocks
        lifetimeBoostsUsed = other.lifetimeBoostsUsed
        lifetimePowerups = other.lifetimePowerups
        lifetimeNearMisses = other.lifetimeNearMisses
        ownedSkins = other.ownedSkins.toMutableSet()
        selectedSkin = other.selectedSkin
        ownedThemes = other.ownedThemes.toMutableSet()
        selectedTheme = other.selectedTheme
        unlockedAchievements = other.unlockedAchievements.toMutableSet()
        dailyDayKey = other.dailyDayKey
        dailyStreak = other.dailyStreak
        bestDailyStreak = other.bestDailyStreak
        dailyCompleted = other.dailyCompleted
        dailyProgress = other.dailyProgress
        dailyGoal = other.dailyGoal
        dailyType = other.dailyType
        sceneryRotation = other.sceneryRotation
        musicEnabled = other.musicEnabled
        sfxEnabled = other.sfxEnabled
        hapticsEnabled = other.hapticsEnabled
        language = other.language
        playerName = other.playerName
    }
}
