package wojtoteka.nightdrive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import wojtoteka.nightdrive.domain.DailyMission
import wojtoteka.nightdrive.domain.MissionType
import wojtoteka.nightdrive.domain.Missions
import wojtoteka.nightdrive.domain.PlayerProfile
import wojtoteka.nightdrive.domain.WorldThemes
import wojtoteka.nightdrive.engine.Coin
import wojtoteka.nightdrive.engine.Enemy
import wojtoteka.nightdrive.engine.EnemyKind
import wojtoteka.nightdrive.engine.GameConfig
import wojtoteka.nightdrive.engine.GameEngine
import wojtoteka.nightdrive.engine.GameEvent
import wojtoteka.nightdrive.engine.GamePhase
import wojtoteka.nightdrive.engine.Rng
import wojtoteka.nightdrive.engine.RunMode
import wojtoteka.nightdrive.engine.WaveKind

/**
 * JVM-only sanity tests for the framework-agnostic engine: the core loop that broke would break
 * these first (coin pickup/award, crash -> game over, shield block). Objects are planted directly
 * in the public entity lists, so no rendering or Android pieces are involved.
 */
class GameEngineTest {

    /** Runs the intro until the car is on screen and the simulation is actually PLAYING. */
    private fun startedEngine(
        profile: PlayerProfile = PlayerProfile(),
        mode: RunMode = RunMode.STANDARD,
        rng: Rng = Rng(),
        nowMs: Long = 0L,
    ): GameEngine {
        val engine = GameEngine(profile, rng)
        engine.startRun(nowMs, mode)
        var guard = 0
        while (engine.phase == GamePhase.INTRO && guard++ < 2000) engine.update(0L)
        assertEquals(GamePhase.PLAYING, engine.phase)
        return engine
    }

    @Test
    fun coinPickupAwardsScoreCoinsAndStreak() {
        val engine = startedEngine()
        engine.coins.clear()
        engine.coins += Coin(engine.player.x, engine.player.y - 10f, 0f)
        var guard = 0
        while (engine.coinsInSession == 0 && guard++ < 300) engine.update(0L)
        assertEquals(1, engine.coinsInSession)
        assertTrue("score should grow after a coin", engine.score >= 1)
        assertEquals(1, engine.streak)
        assertEquals(1, engine.profile.totalCoins)
        assertEquals(1, engine.profile.lifetimeCoins)
    }

    @Test
    fun enemyCollisionTriggersGameOver() {
        val engine = startedEngine()
        engine.enemies.clear()
        engine.enemies += Enemy(engine.player.x, engine.player.y, 0f, 0)
        engine.update(0L)
        assertEquals(GamePhase.GAME_OVER, engine.phase)
        assertTrue(engine.events.contains(GameEvent.Crash))
    }

    @Test
    fun shieldBlocksOneHitAndIsConsumed() {
        val profile = PlayerProfile().apply { shieldCharges = 1 }
        val engine = startedEngine(profile)
        engine.enemies.clear()
        engine.enemies += Enemy(engine.player.x, engine.player.y, 0f, 0)
        engine.update(0L)
        assertEquals(GamePhase.PLAYING, engine.phase)
        assertEquals(0, profile.shieldCharges)
        assertTrue(engine.events.contains(GameEvent.ShieldBlocked))
    }

    /** Drives [enemy] past the parked player and returns the engine once it has left the screen. */
    private fun passEnemy(engine: GameEngine, enemy: Enemy): GameEngine {
        engine.enemies.clear(); engine.coins.clear(); engine.powerups.clear()
        engine.enemies += enemy
        var guard = 0
        while (engine.enemies.contains(enemy) && engine.phase == GamePhase.PLAYING && guard++ < 600) {
            engine.update(0L)
            engine.coins.clear(); engine.powerups.clear()
        }
        return engine
    }

    @Test
    fun carPassingByAHairPaysANearMiss() {
        val engine = startedEngine()
        val p = engine.player
        // Player hitbox (near lane) spans enemy y in about [py - 9, py + 11]: 15 above misses it.
        val engineAfter = passEnemy(engine, Enemy(p.x + 70f, p.y + 15f, 3f, 0))
        assertEquals(GamePhase.PLAYING, engineAfter.phase)
        assertEquals(1, engineAfter.nearMissesInRun)
        assertEquals(1, engineAfter.profile.lifetimeNearMisses)
        assertEquals(1, engineAfter.streak)
        assertTrue("near miss should score", engineAfter.score >= 1)
    }

    @Test
    fun carPassingFarAwayIsNoNearMiss() {
        val engine = startedEngine()
        val p = engine.player
        passEnemy(engine, Enemy(p.x + 70f, p.y + 45f, 3f, 0))
        assertEquals(GamePhase.PLAYING, engine.phase)
        assertEquals(0, engine.nearMissesInRun)
        assertEquals(0, engine.score)
    }

    @Test
    fun truckHitboxIsLongerThanACar() {
        val car = startedEngine()
        car.enemies.clear()
        car.enemies += Enemy(car.player.x + 80f, car.player.y, 0f, 0)
        car.update(0L)
        assertEquals(GamePhase.PLAYING, car.phase)

        val truck = startedEngine()
        truck.enemies.clear()
        truck.enemies += Enemy(truck.player.x + 80f, truck.player.y, 0f, 0, EnemyKind.TRUCK)
        truck.update(0L)
        assertEquals(GamePhase.GAME_OVER, truck.phase)
    }

    @Test
    fun laneChangerDriftsToItsTargetLaneOnceOnScreen() {
        val engine = startedEngine()
        engine.enemies.clear()
        // Far off screen it holds its lane...
        val offScreen = Enemy(engine.halfWidth + 200f, 0f, 0f, 0, EnemyKind.LANE_CHANGER, targetY = 20f)
        // ...on screen (and away from the player) it moves over.
        val onScreen = Enemy(0f, 0f, 0f, 0, EnemyKind.LANE_CHANGER, targetY = 20f)
        engine.enemies += offScreen
        engine.enemies += onScreen
        repeat(120) { engine.update(0L) }
        assertEquals(0f, offScreen.y, 0f)
        assertEquals(20f, onScreen.y, 0f)
        assertTrue(!onScreen.changingLane)
    }

    @Test
    fun firstWaveStartsAfterTwoMinutes() {
        val engine = startedEngine()
        var started: WaveKind? = null
        var guard = 0
        while (started == null && guard++ < 10_000) {
            engine.enemies.clear() // keep the parked player alive
            engine.update(0L)
            started = engine.events.filterIsInstance<GameEvent.WaveStarted>().firstOrNull()?.kind
        }
        assertEquals(GamePhase.PLAYING, engine.phase)
        assertTrue("a wave should start", started != null)
        assertTrue(engine.survivalSeconds >= 120)
        assertEquals(started, engine.waveBanner)
        if (started == WaveKind.CONVOY) {
            // A wall across all lanes but one.
            assertEquals(GameConfig.CONVOY_LANES.size - 1, engine.enemies.size)
        } else {
            assertTrue(engine.fogAmount >= 0f)
        }
    }

    @Test
    fun everyMinuteIsANewStage() {
        val engine = startedEngine()
        var reached = 0
        var guard = 0
        while (reached == 0 && guard++ < 5_000) {
            engine.enemies.clear()
            engine.update(0L)
            reached = engine.events.filterIsInstance<GameEvent.StageReached>().firstOrNull()?.stage ?: 0
        }
        assertEquals(1, reached)
        assertEquals(1, engine.stage)
        assertTrue(engine.survivalSeconds >= 60)
        assertTrue(engine.stageBannerVisible)
    }

    @Test
    fun themeRotationStartsAtTheSelectedTheme() {
        val ids = WorldThemes.rotation(setOf("sunset", "aurora", "inferno"), "inferno").map { it.id }
        assertEquals(listOf("inferno", "sunset", "aurora"), ids)
        assertEquals(1, WorldThemes.rotation(setOf("sunset"), "sunset").size)
    }

    @Test
    fun missionsAreTheSameForEveryoneOnADay() {
        val a = Missions.forDay(20_000L).map { it.type to it.goal }
        val b = Missions.forDay(20_000L).map { it.type to it.goal }
        assertEquals(a, b)
        assertEquals(Missions.COUNT, a.map { it.first }.toSet().size)
    }

    @Test
    fun missionsSurviveStorage() {
        val missions = Missions.forDay(20_001L).also { it[1].progress = 4; it[2].completed = true }
        val back = Missions.decode(Missions.encode(missions))
        assertEquals(missions.map { listOf(it.type, it.goal, it.reward, it.progress, it.completed) },
            back.map { listOf(it.type, it.goal, it.reward, it.progress, it.completed) })
        assertTrue(Missions.decode("garbage").isEmpty())
    }

    @Test
    fun finishingAMissionPaysItsReward() {
        val profile = PlayerProfile()
        val engine = startedEngine(profile)
        profile.missions = mutableListOf(
            DailyMission(MissionType.COINS, 1, 10),
            DailyMission(MissionType.SCORE, 10_000, 15),
            DailyMission(MissionType.SURVIVE, 10_000, 25),
        )
        engine.coins.clear()
        engine.coins += Coin(engine.player.x, engine.player.y - 10f, 0f)
        var completed = false
        var guard = 0
        while (engine.coinsInSession == 0 && guard++ < 300) {
            engine.update(0L)
            completed = completed || engine.events.any { it is GameEvent.MissionCompleted }
        }
        assertTrue(completed)
        assertTrue(profile.missions[0].completed)
        assertEquals(1 + 10, profile.totalCoins)
        assertEquals(1, profile.lifetimeMissions)
    }

    /** Enemy and coin layout after [ticks] of a parked player, as comparable values. */
    private fun worldAfter(engine: GameEngine, ticks: Int): List<Any> {
        repeat(ticks) { if (engine.phase == GamePhase.PLAYING) engine.update(0L) }
        return engine.enemies.map { listOf(it.x, it.y, it.hue, it.kind) } + engine.coins.map { listOf(it.x, it.y) }
    }

    @Test
    fun dailyRouteIsTheSameForEveryPlayer() {
        val day = 20_000L * GameConfig.DAY_MS
        // Different devices (cosmetic seeds) and very different garages...
        val rich = PlayerProfile().apply {
            shieldCharges = 5; pendingMagnetStarts = 3; pendingSlowmoStarts = 3; boostDurationLevelAll = 3
        }
        val a = startedEngine(rich, RunMode.DAILY, Rng(1L), day)
        val b = startedEngine(PlayerProfile(), RunMode.DAILY, Rng(2L), day)
        // ...still see exactly the same road.
        assertEquals(worldAfter(a, 900), worldAfter(b, 900))
        assertEquals(3, rich.pendingMagnetStarts)
        assertEquals(3, rich.pendingSlowmoStarts)
    }

    @Test
    fun dailyRouteIgnoresTheGarageShieldAndRecordsTodaysBest() {
        val profile = PlayerProfile().apply { shieldCharges = 3 }
        val engine = startedEngine(profile, RunMode.DAILY)
        assertEquals(0, engine.shieldCharges)
        engine.enemies.clear()
        engine.enemies += Enemy(engine.player.x, engine.player.y, 0f, 0)
        engine.update(0L)
        assertEquals(GamePhase.GAME_OVER, engine.phase)
        assertEquals(3, profile.shieldCharges)
        assertEquals(1, profile.dailyRunAttempts)
        assertEquals(1, profile.lifetimeDailyRuns)
    }

    @Test
    fun comboMultiplierTiersMatchConfig() {
        assertEquals(1f, GameConfig.comboMultiplier(0), 0f)
        assertEquals(1.5f, GameConfig.comboMultiplier(GameConfig.COMBO_T1), 0f)
        assertEquals(2f, GameConfig.comboMultiplier(GameConfig.COMBO_T2), 0f)
        assertEquals(3f, GameConfig.comboMultiplier(GameConfig.COMBO_T3), 0f)
        assertEquals(4f, GameConfig.comboMultiplier(GameConfig.COMBO_T4), 0f)
    }

    @Test
    fun quittingToMenuClearsTheWorld() {
        val engine = startedEngine()
        engine.coins += Coin(0f, 0f, 1f)
        engine.enemies += Enemy(100f, 0f, 1f, 0)
        engine.enterMenu(0L)
        assertEquals(GamePhase.MENU, engine.phase)
        assertTrue(engine.coins.isEmpty())
        assertTrue(engine.enemies.isEmpty())
        assertTrue(engine.powerups.isEmpty())
    }
}
