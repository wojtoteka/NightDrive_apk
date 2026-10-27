package wojtoteka.nightdrive.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import wojtoteka.nightdrive.domain.AchievementKind
import wojtoteka.nightdrive.domain.Achievements
import wojtoteka.nightdrive.domain.Missions
import wojtoteka.nightdrive.domain.PlayerProfile
import wojtoteka.nightdrive.domain.Skins
import wojtoteka.nightdrive.domain.WorldThemes

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "nightdrive")

/**
 * Serialises the [PlayerProfile] to Preferences DataStore. The profile is loaded once at startup
 * into an in-memory instance the engine mutates freely; [save] flushes the whole thing atomically.
 * This mirrors the original localStorage model while being crash-safe and off the main thread.
 */
class GameStore(private val context: Context) {

    suspend fun load(): PlayerProfile {
        val prefs = context.dataStore.data.first()
        return PlayerProfile().apply {
            highScore = prefs[K.highScore] ?: 0
            totalCoins = prefs[K.totalCoins] ?: 0
            longestStreak = prefs[K.longestStreak] ?: 0
            longestStreakTimeSec = prefs[K.longestTime] ?: 0
            shieldCharges = prefs[K.shield] ?: 0
            pendingMagnetStarts = prefs[K.pendingMagnet] ?: 0
            pendingSlowmoStarts = prefs[K.pendingSlowmo] ?: 0
            pendingDoubleStarts = prefs[K.pendingDouble] ?: 0
            pendingCoinRushStarts = prefs[K.pendingCoinRush] ?: 0
            shieldCapacityUpgrade = prefs[K.shieldCapacity] ?: false
            // Migration: the pre-tier test build stored a single boolean equal to today's +50% tier.
            boostDurationLevelAll = prefs[K.boostDurationAll]
                ?: if (prefs[K.boostDurationLegacy] == true) 2 else 0
            boostDurationLevelMagnet = prefs[K.boostDurationMagnet] ?: 0
            boostDurationLevelSlowmo = prefs[K.boostDurationSlowmo] ?: 0
            boostDurationLevelDouble = prefs[K.boostDurationDouble] ?: 0
            boostDurationLevelCoinRush = prefs[K.boostDurationCoinRush] ?: 0
            lifetimeShieldBlocks = prefs[K.lifetimeShieldBlocks] ?: 0
            lifetimeBoostsUsed = prefs[K.lifetimeBoostsUsed] ?: 0
            lifetimePowerups = prefs[K.lifetimePowerups] ?: 0
            lifetimeNearMisses = prefs[K.lifetimeNearMisses] ?: 0
            ownedSkins = (prefs[K.ownedSkins] ?: setOf(Skins.DEFAULT_ID)).toMutableSet()
            selectedSkin = prefs[K.selectedSkin] ?: Skins.DEFAULT_ID
            ownedThemes = (prefs[K.ownedThemes] ?: setOf(WorldThemes.DEFAULT_ID)).toMutableSet()
            selectedTheme = prefs[K.selectedTheme] ?: WorldThemes.DEFAULT_ID
            unlockedAchievements = (prefs[K.achievements] ?: emptySet()).toMutableSet()
            // lifetimeCoins is new; migrate existing saves so an already-unlocked coin
            // achievement doesn't regress to "in progress" once totalCoins was spent below its goal.
            lifetimeCoins = prefs[K.lifetimeCoins] ?: run {
                val unlockedGoal = Achievements.all
                    .filter { it.kind == AchievementKind.TOTAL_COINS && unlockedAchievements.contains(it.id) }
                    .maxOfOrNull { it.goal } ?: 0
                maxOf(totalCoins, unlockedGoal)
            }
            dailyDayKey = prefs[K.dailyDay] ?: Long.MIN_VALUE
            dailyStreak = prefs[K.dailyStreak] ?: 0
            bestDailyStreak = prefs[K.bestDaily] ?: 0
            // Saves from before missions have none stored: the engine fills in today's three.
            missions = Missions.decode(prefs[K.missions])
            missionsBonusClaimed = prefs[K.missionsBonus] ?: false
            lifetimeMissions = prefs[K.lifetimeMissions] ?: 0
            sceneryRotation = prefs[K.sceneryRotation] ?: true
            musicEnabled = prefs[K.music] ?: true
            sfxEnabled = prefs[K.sfx] ?: true
            hapticsEnabled = prefs[K.haptics] ?: true
            language = prefs[K.language] ?: "system"
            playerName = prefs[K.playerName] ?: ""
            // Keep the owned sets consistent with the free defaults.
            if (!ownedSkins.contains(Skins.DEFAULT_ID)) ownedSkins.add(Skins.DEFAULT_ID)
            if (!ownedThemes.contains(WorldThemes.DEFAULT_ID)) ownedThemes.add(WorldThemes.DEFAULT_ID)
        }
    }

    suspend fun save(p: PlayerProfile) {
        context.dataStore.edit { e ->
            e[K.highScore] = p.highScore
            e[K.totalCoins] = p.totalCoins
            e[K.lifetimeCoins] = p.lifetimeCoins
            e[K.longestStreak] = p.longestStreak
            e[K.longestTime] = p.longestStreakTimeSec
            e[K.shield] = p.shieldCharges
            e[K.pendingMagnet] = p.pendingMagnetStarts
            e[K.pendingSlowmo] = p.pendingSlowmoStarts
            e[K.pendingDouble] = p.pendingDoubleStarts
            e[K.pendingCoinRush] = p.pendingCoinRushStarts
            e[K.shieldCapacity] = p.shieldCapacityUpgrade
            e[K.boostDurationAll] = p.boostDurationLevelAll
            e[K.boostDurationMagnet] = p.boostDurationLevelMagnet
            e[K.boostDurationSlowmo] = p.boostDurationLevelSlowmo
            e[K.boostDurationDouble] = p.boostDurationLevelDouble
            e[K.boostDurationCoinRush] = p.boostDurationLevelCoinRush
            e[K.lifetimeShieldBlocks] = p.lifetimeShieldBlocks
            e[K.lifetimeBoostsUsed] = p.lifetimeBoostsUsed
            e[K.lifetimePowerups] = p.lifetimePowerups
            e[K.lifetimeNearMisses] = p.lifetimeNearMisses
            e[K.ownedSkins] = p.ownedSkins
            e[K.selectedSkin] = p.selectedSkin
            e[K.ownedThemes] = p.ownedThemes
            e[K.selectedTheme] = p.selectedTheme
            e[K.achievements] = p.unlockedAchievements
            e[K.dailyDay] = p.dailyDayKey
            e[K.dailyStreak] = p.dailyStreak
            e[K.bestDaily] = p.bestDailyStreak
            e[K.missions] = Missions.encode(p.missions)
            e[K.missionsBonus] = p.missionsBonusClaimed
            e[K.lifetimeMissions] = p.lifetimeMissions
            // The single daily challenge from before 3.6 is gone for good.
            e.remove(K.legacyDailyDone)
            e.remove(K.legacyDailyProgress)
            e.remove(K.legacyDailyGoal)
            e.remove(K.legacyDailyType)
            e[K.sceneryRotation] = p.sceneryRotation
            e[K.music] = p.musicEnabled
            e[K.sfx] = p.sfxEnabled
            e[K.haptics] = p.hapticsEnabled
            e[K.language] = p.language
            e[K.playerName] = p.playerName
        }
    }

    suspend fun clearProgress(p: PlayerProfile) {
        // Reset gameplay progress but keep user settings (music/sfx/haptics/language).
        p.highScore = 0
        p.totalCoins = 0
        p.lifetimeCoins = 0
        p.longestStreak = 0
        p.longestStreakTimeSec = 0
        p.shieldCharges = 0
        p.pendingMagnetStarts = 0
        p.pendingSlowmoStarts = 0
        p.pendingDoubleStarts = 0
        p.pendingCoinRushStarts = 0
        p.shieldCapacityUpgrade = false
        p.boostDurationLevelAll = 0
        p.boostDurationLevelMagnet = 0
        p.boostDurationLevelSlowmo = 0
        p.boostDurationLevelDouble = 0
        p.boostDurationLevelCoinRush = 0
        p.lifetimeShieldBlocks = 0
        p.lifetimeBoostsUsed = 0
        p.lifetimePowerups = 0
        p.lifetimeNearMisses = 0
        p.ownedSkins = mutableSetOf(Skins.DEFAULT_ID)
        p.selectedSkin = Skins.DEFAULT_ID
        p.ownedThemes = mutableSetOf(WorldThemes.DEFAULT_ID)
        p.selectedTheme = WorldThemes.DEFAULT_ID
        p.unlockedAchievements = mutableSetOf()
        p.missions = if (p.dailyDayKey != Long.MIN_VALUE) Missions.forDay(p.dailyDayKey) else mutableListOf()
        p.missionsBonusClaimed = false
        p.lifetimeMissions = 0
        save(p)
    }

    private object K {
        val highScore = intPreferencesKey("high_score")
        val totalCoins = intPreferencesKey("total_coins")
        val lifetimeCoins = intPreferencesKey("lifetime_coins")
        val longestStreak = intPreferencesKey("longest_streak")
        val longestTime = intPreferencesKey("longest_time")
        val shield = intPreferencesKey("shield_charges")
        val pendingMagnet = intPreferencesKey("pending_magnet_starts")
        val pendingSlowmo = intPreferencesKey("pending_slowmo_starts")
        val pendingDouble = intPreferencesKey("pending_double_starts")
        val pendingCoinRush = intPreferencesKey("pending_coinrush_starts")
        val shieldCapacity = booleanPreferencesKey("shield_capacity_upgrade")
        val boostDurationLegacy = booleanPreferencesKey("boost_duration_upgrade")
        val boostDurationAll = intPreferencesKey("boost_duration_all")
        val boostDurationMagnet = intPreferencesKey("boost_duration_magnet")
        val boostDurationSlowmo = intPreferencesKey("boost_duration_slowmo")
        val boostDurationDouble = intPreferencesKey("boost_duration_double")
        val boostDurationCoinRush = intPreferencesKey("boost_duration_coinrush")
        val lifetimeShieldBlocks = intPreferencesKey("lifetime_shield_blocks")
        val lifetimeBoostsUsed = intPreferencesKey("lifetime_boosts_used")
        val lifetimePowerups = intPreferencesKey("lifetime_powerups")
        val lifetimeNearMisses = intPreferencesKey("lifetime_near_misses")
        val ownedSkins = stringSetPreferencesKey("owned_skins")
        val selectedSkin = stringPreferencesKey("selected_skin")
        val ownedThemes = stringSetPreferencesKey("owned_themes")
        val selectedTheme = stringPreferencesKey("selected_theme")
        val achievements = stringSetPreferencesKey("achievements")
        val dailyDay = longPreferencesKey("daily_day")
        val dailyStreak = intPreferencesKey("daily_streak")
        val bestDaily = intPreferencesKey("best_daily_streak")
        val missions = stringPreferencesKey("daily_missions")
        val missionsBonus = booleanPreferencesKey("daily_missions_bonus")
        val lifetimeMissions = intPreferencesKey("lifetime_missions")
        val legacyDailyDone = booleanPreferencesKey("daily_completed")
        val legacyDailyProgress = intPreferencesKey("daily_progress")
        val legacyDailyGoal = intPreferencesKey("daily_goal")
        val legacyDailyType = intPreferencesKey("daily_type")
        val sceneryRotation = booleanPreferencesKey("scenery_rotation")
        val music = booleanPreferencesKey("music_enabled")
        val sfx = booleanPreferencesKey("sfx_enabled")
        val haptics = booleanPreferencesKey("haptics_enabled")
        val language = stringPreferencesKey("language")
        val playerName = stringPreferencesKey("player_name")
    }
}
