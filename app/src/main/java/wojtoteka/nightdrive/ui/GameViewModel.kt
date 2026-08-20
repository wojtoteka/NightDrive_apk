package wojtoteka.nightdrive.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import wojtoteka.nightdrive.audio.AudioController
import wojtoteka.nightdrive.audio.Sfx
import wojtoteka.nightdrive.data.GameStore
import wojtoteka.nightdrive.domain.Achievements
import wojtoteka.nightdrive.domain.Language
import wojtoteka.nightdrive.domain.PlayerProfile
import wojtoteka.nightdrive.domain.PowerUpType
import wojtoteka.nightdrive.domain.BoostDurationUpgrade
import wojtoteka.nightdrive.domain.SHIELD_CAPACITY_PRICE
import wojtoteka.nightdrive.domain.SHIELD_PRICE
import wojtoteka.nightdrive.domain.Skins
import wojtoteka.nightdrive.engine.GameConfig
import wojtoteka.nightdrive.engine.GameEngine
import wojtoteka.nightdrive.engine.GameEvent
import wojtoteka.nightdrive.engine.GamePhase
import wojtoteka.nightdrive.domain.WorldThemes
import wojtoteka.nightdrive.net.LeaderboardEntry
import wojtoteka.nightdrive.net.LeaderboardState
import wojtoteka.nightdrive.net.SubmitState

/**
 * Single source of truth for the UI. Owns the engine, drives the fixed-timestep loop from the
 * Compose frame clock, routes engine events to audio/haptics, and persists progress. The heavy,
 * per-frame world state stays in the engine (read directly by the renderer); only discrete UI
 * state is exposed as Compose state so recomposition stays cheap.
 */
class GameViewModel(app: Application) : AndroidViewModel(app) {

    private val store = GameStore(app)
    private val audio = AudioController(app)
    private val haptics = wojtoteka.nightdrive.util.Haptics(app)
    private val leaderboardApi = wojtoteka.nightdrive.net.LeaderboardApi()

    private val profile = PlayerProfile()
    val engine = GameEngine(profile)

    var isReady by mutableStateOf(false); private set
    var destination by mutableStateOf(Destination.MENU); private set
    val frameTick = mutableIntStateOf(0)
    var hud by mutableStateOf(HudState()); private set
    var profileState by mutableStateOf(ProfileSnapshot()); private set
    var achievementToastKey by mutableStateOf<String?>(null); private set
    var powerUpToast by mutableStateOf<PowerUpType?>(null); private set
    var language by mutableStateOf(Language.SYSTEM); private set

    // Online leaderboard
    var leaderboardState by mutableStateOf<LeaderboardState>(LeaderboardState.Loading); private set
    var scoreQualifies by mutableStateOf(false); private set
    var submitState by mutableStateOf<SubmitState>(SubmitState.Idle); private set

    // Rewarded ads: "double your coins" and "continue driving" are each claimable once per run.
    var adRewardClaimed by mutableStateOf(false); private set
    var adReviveUsed by mutableStateOf(false); private set
    val lastNickname: String get() = profile.playerName
    private var sessionId: String? = null
    private var gameOverScore = 0
    private var cachedTop: List<LeaderboardEntry> = emptyList()

    val worldAnimMs: Float get() = animMs
    val selectedSkinId: String get() = profile.selectedSkin

    private var accumulatorMs = 0f
    private var animMs = 0f
    private var toastJob: Job? = null
    private var powerUpToastJob: Job? = null

    // Live input intents — slide-to-steer surface
    private var inLeft = false
    private var inRight = false
    private var inUp = false
    private var inDown = false

    // On-screen d-pad; when any pad button is held it takes priority over slide steering
    // (the slide surface also sees pad touches, so priority avoids the two fighting).
    private var padLeft = false
    private var padRight = false
    private var padUp = false
    private var padDown = false

    /** Debug-only screenshot staging, requested via launch intent before the profile loads. */
    private var demoRequested = false

    init {
        audio.loadSfx()
        viewModelScope.launch {
            profile.copyFrom(store.load())
            applySettings()
            language = Language.fromCode(profile.language)
            engine.enterMenu(System.currentTimeMillis())
            refreshProfileState()
            isReady = true
            if (profile.musicEnabled) audio.startMusic()
            store.save(profile) // persist any daily rollover done in enterMenu
            if (demoRequested) stageDemo()
        }
    }

    /** Debug builds only: stage the frozen marketing scene (see GameEngine.stageDemoScene). */
    fun requestDemo() {
        if (!wojtoteka.nightdrive.BuildConfig.DEBUG) return
        if (isReady) stageDemo() else demoRequested = true
    }

    private fun stageDemo() {
        profile.shieldCharges = 2   // makes the power-up HUD panel show shield charges too
        engine.stageDemoScene()
        updateHud()
        destination = Destination.GAME
    }

    // ---- Frame loop ------------------------------------------------------------------------

    fun onFrame(deltaNanos: Long) {
        if (!isReady) return
        val dtMs = deltaNanos / 1_000_000f
        animMs += dtMs
        accumulatorMs += dtMs.coerceAtMost(100f) // avoid a spiral of death after a long stall
        if (padLeft || padRight || padUp || padDown) {
            engine.input(padLeft, padRight, padUp, padDown)
        } else {
            engine.input(inLeft, inRight, inUp, inDown)
        }
        var steps = 0
        val step = GameConfig.TICK_MS
        while (accumulatorMs >= step && steps < 5) {
            engine.update(System.currentTimeMillis())
            drainEvents()
            accumulatorMs -= step
            steps++
        }
        frameTick.intValue++
        updateHud()
    }

    private fun drainEvents() {
        for (i in engine.events.indices) {
            when (val ev = engine.events[i]) {
                GameEvent.CoinCollected -> { audio.play(Sfx.COIN); haptics.tick() }
                is GameEvent.PowerUpCollected -> {
                    audio.play(Sfx.POINT); haptics.light()
                    powerUpToast = ev.type
                    powerUpToastJob?.cancel()
                    powerUpToastJob = viewModelScope.launch { delay(1400); powerUpToast = null }
                }
                GameEvent.ShieldBlocked -> { audio.play(Sfx.POINT, 0.5f); haptics.medium() }
                GameEvent.NewHighScore -> {}
                GameEvent.Crash -> {
                    audio.play(Sfx.EXPLOSION)
                    audio.play(Sfx.DEFEAT)
                    haptics.heavy()
                    onGameOver()
                    evaluateQualification(engine.score)
                }
                is GameEvent.AchievementUnlocked -> {
                    audio.play(Sfx.COIN, 0.6f)
                    haptics.success()
                    showAchievementToast(ev.id)
                }
            }
        }
    }

    private fun updateHud() {
        // No screen reads live HUD data while the engine idles in the menu phase (the HUD only
        // renders on the GAME destination), so skip rebuilding the snapshot 60× a second there.
        // The first frame after any phase/destination change still lands here and refreshes it.
        if (destination != Destination.GAME &&
            engine.phase == GamePhase.MENU && hud.phase == GamePhase.MENU
        ) return
        val pus = ArrayList<HudPowerUp>(5)
        if (profile.shieldCharges > 0) {
            pus += HudPowerUp(PowerUpType.SHIELD, true, profile.shieldCharges, profile.shieldCharges, 0f)
        }
        if (engine.doubleActive) pus += powerUpHud(PowerUpType.DOUBLE)
        if (engine.magnetActive) pus += powerUpHud(PowerUpType.MAGNET)
        if (engine.slowmoActive) pus += powerUpHud(PowerUpType.SLOWMO)
        if (engine.coinRushActive) pus += powerUpHud(PowerUpType.COIN_RUSH)
        hud = HudState(
            phase = engine.phase,
            paused = engine.paused,
            score = engine.score,
            coinsInSession = engine.coinsInSession,
            comboMultiplier = engine.comboMultiplier,
            streak = engine.streak,
            powerups = pus,
            dailyType = profile.dailyType,
            dailyGoal = profile.dailyGoal,
            dailyProgress = profile.dailyProgress,
            dailyCompleted = profile.dailyCompleted,
            highScore = profile.highScore,
        )
    }

    private fun powerUpHud(type: PowerUpType) = HudPowerUp(
        type = type, isCharge = false, chargeCount = 0,
        remainingSeconds = engine.powerupRemainingSeconds(type),
        fraction = engine.powerupRemainingFraction(type),
    )

    // ---- Navigation & actions --------------------------------------------------------------

    fun setViewport(halfWidthUnits: Float) = engine.setViewport(halfWidthUnits)

    fun setInput(left: Boolean, right: Boolean, up: Boolean, down: Boolean) {
        inLeft = left; inRight = right; inUp = up; inDown = down
    }

    fun setPad(dir: PadDir, pressed: Boolean) {
        when (dir) {
            PadDir.LEFT -> padLeft = pressed
            PadDir.RIGHT -> padRight = pressed
            PadDir.UP -> padUp = pressed
            PadDir.DOWN -> padDown = pressed
        }
    }

    fun clearInput() {
        inLeft = false; inRight = false; inUp = false; inDown = false
        padLeft = false; padRight = false; padUp = false; padDown = false
    }

    fun goTo(dest: Destination) {
        // Navigating anywhere outside gameplay (e.g. game-over -> leaderboard) must reset the
        // world, otherwise leftover cars/coins from the last run linger behind menu screens.
        if (dest != Destination.GAME && engine.phase != GamePhase.MENU) {
            clearInput()
            engine.enterMenu(System.currentTimeMillis())
            persist()
            refreshProfileState()
        }
        destination = dest
    }

    fun startGame() {
        clearInput()
        adRewardClaimed = false
        adReviveUsed = false
        engine.startRun(System.currentTimeMillis())
        destination = Destination.GAME
        if (profile.musicEnabled) audio.resumeMusic()
        beginLeaderboardRun()
    }

    fun restartGame() {
        clearInput()
        adRewardClaimed = false
        adReviveUsed = false
        engine.startRun(System.currentTimeMillis())
        beginLeaderboardRun()
    }

    /** Rewarded-ad bonus: banks this run's coins a second time ("double coins"). */
    fun grantAdCoinDouble() {
        if (adRewardClaimed) return
        val bonus = engine.coinsInSession
        if (bonus <= 0) return
        adRewardClaimed = true
        profile.totalCoins += bonus
        profile.lifetimeCoins += bonus
        audio.play(Sfx.COIN)
        haptics.success()
        persist()
        refreshProfileState()
    }

    /** Rewarded-ad continue: resumes the crashed run in place (once per run). */
    fun grantAdRevive() {
        if (adReviveUsed || engine.phase != GamePhase.GAME_OVER) return
        adReviveUsed = true
        // Clear any stale game-over leaderboard UI; the next crash re-evaluates from scratch.
        scoreQualifies = false
        if (submitState !is SubmitState.Done) submitState = SubmitState.Idle
        engine.revive()
        audio.play(Sfx.POINT)
        haptics.success()
        if (profile.musicEnabled) audio.resumeMusic()
    }

    private var runToken = 0

    private fun beginLeaderboardRun() {
        scoreQualifies = false
        submitState = SubmitState.Idle
        sessionId = null
        val token = ++runToken
        // Retry a couple of times so a brief hiccup at run start doesn't cost the session.
        viewModelScope.launch {
            for (delayMs in longArrayOf(0L, 3000L, 8000L)) {
                delay(delayMs)
                if (token != runToken || sessionId != null) return@launch
                sessionId = leaderboardApi.startSession()
                if (sessionId != null) return@launch
            }
        }
    }

    // ---- Leaderboard -----------------------------------------------------------------------

    fun fetchLeaderboard() {
        leaderboardState = LeaderboardState.Loading
        viewModelScope.launch {
            leaderboardState = try {
                val top = leaderboardApi.fetchTop()
                cachedTop = top
                LeaderboardState.Success(top)
            } catch (_: Exception) {
                LeaderboardState.Offline
            }
        }
    }

    /**
     * After a run, work out whether the score would land in the Top 10 (prompts the nickname UI).
     * Qualification depends only on the leaderboard itself — the play session is resolved (and
     * retried) at submit time, so a slow session handshake never hides the name field.
     */
    private fun evaluateQualification(finalScore: Int) {
        gameOverScore = finalScore
        scoreQualifies = false
        if (finalScore <= 0) return
        viewModelScope.launch {
            try {
                val top = leaderboardApi.fetchTop()
                cachedTop = top
                scoreQualifies = top.size < 10 || finalScore > top.minOf { it.score }
            } catch (_: Exception) {
                // Can't reach the ranking — tell the player why there's no entry field.
                submitState = SubmitState.Offline
            }
        }
    }

    fun submitNickname(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        profile.playerName = trimmed
        persist()
        submitState = SubmitState.Submitting
        viewModelScope.launch {
            // Last-chance session: if the run-start handshake failed, try once more now.
            val sid = sessionId ?: leaderboardApi.startSession().also { sessionId = it }
            if (sid == null) { submitState = SubmitState.Offline; return@launch }
            submitState = try {
                val r = leaderboardApi.submit(sid, trimmed, gameOverScore)
                if (r.accepted) {
                    sessionId = null
                    scoreQualifies = false
                    fetchLeaderboard()
                    SubmitState.Done(r.rank)
                } else {
                    SubmitState.Failed
                }
            } catch (_: Exception) {
                SubmitState.Offline
            }
        }
    }

    fun togglePause() { engine.togglePause() }
    fun setPaused(paused: Boolean) { engine.setPaused(paused) }

    fun quitToMenu() {
        clearInput()
        engine.enterMenu(System.currentTimeMillis())
        destination = Destination.MENU
        persist()
        refreshProfileState()
    }

    private fun onGameOver() {
        persist()
        refreshProfileState()
    }

    // ---- Shop ------------------------------------------------------------------------------

    /** Returns true if a purchase happened (for a success chirp), false if it was just equipped. */
    fun onSkinTapped(id: String): Boolean {
        if (profile.ownsSkin(id)) {
            profile.selectedSkin = id
            persist(); refreshProfileState()
            haptics.light()
            return false
        }
        val skin = Skins.byId(id)
        if (profile.totalCoins >= skin.price) {
            profile.totalCoins -= skin.price
            profile.ownedSkins.add(id)
            profile.selectedSkin = id
            unlockShopAchievements()
            persist(); refreshProfileState()
            audio.play(Sfx.COIN, 0.7f); haptics.success()
            return true
        }
        haptics.medium()
        return false
    }

    /** Same buy-or-equip semantics as skins, for the world colour themes. */
    fun onThemeTapped(id: String): Boolean {
        if (profile.ownedThemes.contains(id)) {
            profile.selectedTheme = id
            persist(); refreshProfileState()
            haptics.light()
            return false
        }
        val theme = WorldThemes.byId(id)
        if (profile.totalCoins >= theme.price) {
            profile.totalCoins -= theme.price
            profile.ownedThemes.add(id)
            profile.selectedTheme = id
            persist(); refreshProfileState()
            audio.play(Sfx.COIN, 0.7f); haptics.success()
            return true
        }
        haptics.medium()
        return false
    }

    /**
     * Achievements whose progress moves in the shop (skin collection) would otherwise unlock only
     * on the next engine tick; check them here so the toast fires the moment the purchase lands.
     */
    private fun unlockShopAchievements() {
        for (a in Achievements.all) {
            if (a.kind != wojtoteka.nightdrive.domain.AchievementKind.SKIN_COUNT) continue
            if (profile.unlockedAchievements.contains(a.id)) continue
            if (profile.ownedSkins.size >= a.goal) {
                profile.unlockedAchievements.add(a.id)
                profile.totalCoins += a.reward
                audio.play(Sfx.COIN, 0.6f)
                haptics.success()
                showAchievementToast(a.id)
            }
        }
    }

    /** Buys one shield charge with coins, capped at [PlayerProfile.shieldMax] like the in-run pickup. */
    fun buyShieldCharge(): Boolean {
        if (profile.shieldCharges >= profile.shieldMax || profile.totalCoins < SHIELD_PRICE) {
            haptics.medium()
            return false
        }
        profile.totalCoins -= SHIELD_PRICE
        profile.shieldCharges += 1
        persist(); refreshProfileState()
        audio.play(Sfx.COIN, 0.7f); haptics.success()
        return true
    }

    /**
     * Rewarded ad -> one shield charge. No cooldown, no daily cap: watch the ad, get a charge,
     * repeat — the only ceiling is [PlayerProfile.shieldMax], the same one every other shield source
     * respects, plus the natural gate of a new ad having to load before it can be shown again.
     */
    fun grantAdShield() {
        if (profile.shieldCharges >= profile.shieldMax) return
        profile.shieldCharges += 1
        persist(); refreshProfileState()
        audio.play(Sfx.COIN); haptics.success()
    }

    /** Buys one stock of a starter boost; consumed automatically at the start of the next run. */
    fun buyBoost(boost: wojtoteka.nightdrive.domain.Boost): Boolean {
        if (profile.totalCoins < boost.price) {
            haptics.medium()
            return false
        }
        profile.totalCoins -= boost.price
        when (boost.type) {
            PowerUpType.MAGNET -> profile.pendingMagnetStarts += 1
            PowerUpType.SLOWMO -> profile.pendingSlowmoStarts += 1
            PowerUpType.DOUBLE -> profile.pendingDoubleStarts += 1
            PowerUpType.COIN_RUSH -> profile.pendingCoinRushStarts += 1
            PowerUpType.SHIELD -> {} // shield charges are bought via buyShieldCharge(), not the boost catalog
        }
        persist(); refreshProfileState()
        audio.play(Sfx.COIN, 0.7f); haptics.success()
        return true
    }

    /** One-time permanent purchase: raises the shield charge cap from [GameConfig.SHIELD_MAX]
     *  to [GameConfig.SHIELD_MAX_UPGRADED]. */
    fun buyShieldCapacityUpgrade(): Boolean {
        if (profile.shieldCapacityUpgrade || profile.totalCoins < SHIELD_CAPACITY_PRICE) {
            haptics.medium()
            return false
        }
        profile.totalCoins -= SHIELD_CAPACITY_PRICE
        profile.shieldCapacityUpgrade = true
        persist(); refreshProfileState()
        audio.play(Sfx.COIN, 0.7f); haptics.success()
        return true
    }

    /** Buys the next "+% duration on ALL power-ups" tier (see [BoostDurationUpgrade]). */
    fun buyBoostDurationAll(): Boolean {
        val price = BoostDurationUpgrade.nextPriceAll(profile.boostDurationLevelAll)
        if (price == null || profile.totalCoins < price) {
            haptics.medium()
            return false
        }
        profile.totalCoins -= price
        profile.boostDurationLevelAll += 1
        persist(); refreshProfileState()
        audio.play(Sfx.COIN, 0.7f); haptics.success()
        return true
    }

    /** Buys the next "+% duration" tier for a single power-up (see [BoostDurationUpgrade]). */
    fun buyBoostDurationFor(type: PowerUpType): Boolean {
        val price = BoostDurationUpgrade.nextPriceEach(profile.boostDurationLevel(type))
        if (price == null || profile.totalCoins < price) {
            haptics.medium()
            return false
        }
        when (type) {
            PowerUpType.MAGNET -> profile.boostDurationLevelMagnet += 1
            PowerUpType.SLOWMO -> profile.boostDurationLevelSlowmo += 1
            PowerUpType.DOUBLE -> profile.boostDurationLevelDouble += 1
            PowerUpType.COIN_RUSH -> profile.boostDurationLevelCoinRush += 1
            PowerUpType.SHIELD -> { haptics.medium(); return false } // shield has no duration to extend
        }
        profile.totalCoins -= price
        persist(); refreshProfileState()
        audio.play(Sfx.COIN, 0.7f); haptics.success()
        return true
    }

    // ---- Settings --------------------------------------------------------------------------

    fun setMusicEnabled(v: Boolean) { profile.musicEnabled = v; audio.musicEnabled = v; persist(); refreshProfileState() }
    fun setSfxEnabled(v: Boolean) { profile.sfxEnabled = v; audio.sfxEnabled = v; persist(); refreshProfileState() }
    fun setHapticsEnabled(v: Boolean) { profile.hapticsEnabled = v; haptics.enabled = v; if (v) haptics.light(); persist(); refreshProfileState() }

    fun changeLanguage(lang: Language) {
        profile.language = lang.code
        language = lang
        persist(); refreshProfileState()
    }

    fun resetProgress() {
        viewModelScope.launch {
            store.clearProgress(profile)
            engine.enterMenu(System.currentTimeMillis())
            refreshProfileState()
        }
    }

    // ---- Lifecycle -------------------------------------------------------------------------

    fun onAppPause() {
        if (engine.phase == GamePhase.PLAYING) engine.setPaused(true)
        audio.pauseMusic()
        persist()
    }

    fun onAppResume() {
        if (profile.musicEnabled) audio.resumeMusic()
    }

    override fun onCleared() {
        persist()
        audio.release()
        super.onCleared()
    }

    // ---- Helpers ---------------------------------------------------------------------------

    private fun applySettings() {
        audio.musicEnabled = profile.musicEnabled
        audio.sfxEnabled = profile.sfxEnabled
        haptics.enabled = profile.hapticsEnabled
    }

    private fun persist() { viewModelScope.launch { store.save(profile) } }

    private fun refreshProfileState() {
        profileState = ProfileSnapshot(
            highScore = profile.highScore,
            totalCoins = profile.totalCoins,
            lifetimeCoins = profile.lifetimeCoins,
            bestTimeSec = profile.longestStreakTimeSec,
            longestStreak = profile.longestStreak,
            shieldCharges = profile.shieldCharges,
            shieldMax = profile.shieldMax,
            shieldCapacityUpgrade = profile.shieldCapacityUpgrade,
            boostDurationLevelAll = profile.boostDurationLevelAll,
            boostDurationLevelMagnet = profile.boostDurationLevelMagnet,
            boostDurationLevelSlowmo = profile.boostDurationLevelSlowmo,
            boostDurationLevelDouble = profile.boostDurationLevelDouble,
            boostDurationLevelCoinRush = profile.boostDurationLevelCoinRush,
            pendingMagnetStarts = profile.pendingMagnetStarts,
            pendingSlowmoStarts = profile.pendingSlowmoStarts,
            pendingDoubleStarts = profile.pendingDoubleStarts,
            pendingCoinRushStarts = profile.pendingCoinRushStarts,
            lifetimeShieldBlocks = profile.lifetimeShieldBlocks,
            lifetimeBoostsUsed = profile.lifetimeBoostsUsed,
            lifetimePowerups = profile.lifetimePowerups,
            dailyType = profile.dailyType,
            dailyGoal = profile.dailyGoal,
            dailyProgress = profile.dailyProgress,
            dailyCompleted = profile.dailyCompleted,
            dailyStreak = profile.dailyStreak,
            bestDailyStreak = profile.bestDailyStreak,
            ownedSkins = profile.ownedSkins.toSet(),
            selectedSkin = profile.selectedSkin,
            ownedThemes = profile.ownedThemes.toSet(),
            selectedTheme = profile.selectedTheme,
            unlockedAchievements = profile.unlockedAchievements.toSet(),
            musicEnabled = profile.musicEnabled,
            sfxEnabled = profile.sfxEnabled,
            hapticsEnabled = profile.hapticsEnabled,
            language = Language.fromCode(profile.language),
        )
    }

    private fun showAchievementToast(id: String) {
        val key = Achievements.all.firstOrNull { it.id == id }?.nameKey ?: return
        achievementToastKey = key
        toastJob?.cancel()
        toastJob = viewModelScope.launch { delay(3500); achievementToastKey = null }
    }
}
