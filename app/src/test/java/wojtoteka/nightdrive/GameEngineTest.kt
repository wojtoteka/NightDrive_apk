package wojtoteka.nightdrive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import wojtoteka.nightdrive.domain.CarBodies
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

    /** Keeps the parked player alive until an event starts; returns its kind. */
    private fun nextWave(engine: GameEngine): WaveKind {
        var guard = 0
        while (guard++ < 200_000) {
            engine.enemies.clear()
            engine.update(0L)
            engine.events.filterIsInstance<GameEvent.WaveStarted>().firstOrNull()?.let { return it.kind }
        }
        error("no road event started")
    }

    private fun waveOf(kind: WaveKind, engine: GameEngine = startedEngine()): GameEngine {
        while (nextWave(engine) != kind) { /* keep driving */ }
        return engine
    }

    @Test
    fun firstRoadEventIsALightOneInTheFirstMinute() {
        val engine = startedEngine()
        val first = nextWave(engine)
        assertEquals(GamePhase.PLAYING, engine.phase)
        assertTrue(first in WaveKind.early)
        assertEquals(45, engine.survivalSeconds)
        assertEquals(first, engine.waveBanner)
    }

    @Test
    fun convoyAndFogWaitForTwoMinutes() {
        val engine = startedEngine()
        var hard = nextWave(engine)
        while (hard in WaveKind.early) hard = nextWave(engine)
        assertTrue(engine.survivalSeconds >= 120)
    }

    @Test
    fun convoyIsAWallWithOneGap() {
        val engine = waveOf(WaveKind.CONVOY)
        assertEquals(GameConfig.CONVOY_LANES.size - 1, engine.enemies.size)
    }

    @Test
    fun policeComeFromBehindAimedAtThePlayer() {
        val engine = waveOf(WaveKind.POLICE)
        engine.update(0L)
        val police = engine.enemies.single { it.kind == EnemyKind.POLICE }
        assertTrue(police.x < -engine.halfWidth)
        assertEquals(engine.player.y, police.y, 0f)
        assertTrue(GameEvent.PoliceIncoming in engine.events)
        val before = police.x
        engine.update(0L)
        assertTrue("police drive to the right", police.x > before)
    }

    @Test
    fun policeOffTheRightEdgeLeaveTheRoad() {
        val engine = startedEngine()
        engine.enemies.clear()
        val police = Enemy(engine.halfWidth + GameConfig.DESPAWN_MARGIN - 1f, 25f, GameConfig.POLICE_SPEED, 0, EnemyKind.POLICE)
        engine.enemies += police
        engine.update(0L)
        assertTrue(police !in engine.enemies)
    }

    @Test
    fun roadworksCloseOneHalfOfTheRoad() {
        val engine = waveOf(WaveKind.ROADWORKS)
        engine.update(0L)
        val lanes = engine.enemies.filter { it.kind == EnemyKind.BARRIER }.map { it.y }.toSet()
        assertTrue(lanes == GameConfig.ROADWORKS_UPPER_LANES.toSet() || lanes == GameConfig.ROADWORKS_LOWER_LANES.toSet())
    }

    @Test
    fun barriersDoNotCountAgainstTheTrafficCap() {
        val engine = startedEngine()
        repeat(GameConfig.MAX_ENEMIES + 2) { engine.enemies += Enemy(400f, 20f, 0f, 0, EnemyKind.BARRIER) }
        val barriers = engine.enemies.size
        var guard = 0
        while (engine.enemies.size == barriers && guard++ < 600) engine.update(0L)
        assertTrue("traffic should still spawn", engine.enemies.any { it.kind != EnemyKind.BARRIER })
    }

    @Test
    fun goldRibbonCoinsPayFlatPointsEvenAfterTheWave() {
        val engine = startedEngine()
        engine.coins.clear()
        collectCoins(engine, GameConfig.COMBO_T2)
        assertTrue(engine.comboMultiplier > 1f)
        assertEquals(null, engine.wave)
        val score = engine.score
        engine.coins += Coin(engine.player.x, engine.player.y - 10f, 0f, gold = true)
        val coins = engine.coinsInSession
        var guard = 0
        while (engine.coinsInSession == coins && guard++ < 300) { engine.enemies.clear(); engine.update(0L) }
        assertEquals(coins + 1, engine.coinsInSession)
        assertEquals(score + 1, engine.score)
    }

    @Test
    fun goldWaveStringsRibbonCoins() {
        val engine = waveOf(WaveKind.GOLD)
        engine.coins.clear()
        var guard = 0
        while (engine.coins.isEmpty() && guard++ < 120) { engine.enemies.clear(); engine.update(0L) }
        assertTrue(engine.coins.isNotEmpty())
        assertTrue(engine.coins.all { it.gold })
    }

    @Test
    fun coinPatternsAppearAfterTheOpening() {
        val engine = startedEngine()
        var most = 0
        repeat(12_000) {
            engine.enemies.clear(); engine.powerups.clear()
            engine.update(0L)
            val inRow = engine.coins.count { it.x >= GameConfig.COIN_SPAWN_X - 1f }
            if (inRow > most) most = inRow
        }
        assertEquals(GamePhase.PLAYING, engine.phase)
        assertEquals(GameConfig.COIN_PATTERN_LENGTH, most)
    }

    private fun fillNitro(engine: GameEngine) {
        repeat(GameConfig.NITRO_NEAR_MISSES) {
            val p = engine.player
            passEnemy(engine, Enemy(p.x + 70f, p.y + 15f, 3f, 0))
        }
        assertTrue(engine.nitroReady)
    }

    @Test
    fun nearMissesFillTheNitroTank() {
        val engine = startedEngine()
        assertEquals(0f, engine.nitroCharge, 0f)
        val p = engine.player
        passEnemy(engine, Enemy(p.x + 70f, p.y + 15f, 3f, 0))
        assertEquals(1f / GameConfig.NITRO_NEAR_MISSES, engine.nitroCharge, 0.001f)
        assertTrue(!engine.nitroReady)
    }

    @Test
    fun nitroIsIgnoredUntilTheTankIsFull() {
        val engine = startedEngine()
        engine.requestNitro()
        engine.enemies.clear(); engine.update(0L)
        assertTrue(!engine.nitroActive)
        assertEquals(0, engine.nitroUsesInRun)
    }

    @Test
    fun nitroPloughsThroughTrafficAndEmptiesTheTank() {
        val engine = startedEngine()
        fillNitro(engine)
        engine.enemies.clear(); engine.coins.clear(); engine.powerups.clear()
        engine.requestNitro()
        engine.enemies += Enemy(engine.player.x, engine.player.y, 0f, 0)
        val before = engine.score
        engine.update(0L)
        assertTrue(engine.nitroActive)
        assertTrue(GameEvent.NitroStarted in engine.events)
        assertTrue(engine.events.any { it is GameEvent.NitroSmash })
        assertEquals(GamePhase.PLAYING, engine.phase)
        assertEquals(0f, engine.nitroCharge, 0f)
        assertEquals(1, engine.smashesInRun)
        assertEquals(1, engine.profile.lifetimeSmashes)
        assertTrue(engine.score > before)
    }

    @Test
    fun nitroBurnRunsOutAndThenCrashesCountAgain() {
        val engine = startedEngine()
        fillNitro(engine)
        engine.enemies.clear(); engine.coins.clear(); engine.powerups.clear()
        engine.requestNitro()
        engine.update(0L)
        assertTrue(engine.nitroActive)
        var guard = 0
        while (engine.nitroActive && guard++ < 1_000) { engine.enemies.clear(); engine.update(0L) }
        assertTrue(!engine.nitroActive)
        engine.enemies.clear()
        engine.enemies += Enemy(engine.player.x, engine.player.y, 0f, 0)
        engine.update(0L)
        assertEquals(GamePhase.GAME_OVER, engine.phase)
    }

    @Test
    fun gameOverKnowsWhatHitYou() {
        val engine = startedEngine()
        assertEquals(null, engine.crashedBy)
        engine.enemies.clear()
        engine.enemies += Enemy(engine.player.x, engine.player.y, 0f, 0, EnemyKind.TRUCK)
        engine.update(0L)
        assertEquals(EnemyKind.TRUCK, engine.crashedBy)
        engine.startRun(0L)
        assertEquals(null, engine.crashedBy)
    }

    @Test
    fun todaysBestDailyRunComesBackAsAGhost() {
        val day = 20_000L * GameConfig.DAY_MS
        val profile = PlayerProfile()
        val first = startedEngine(profile, RunMode.DAILY, Rng(1L), day)
        first.coins.clear()
        repeat(120) { first.enemies.clear(); first.coins.clear(); first.powerups.clear(); first.update(day) }
        collectCoins(first, 1)
        first.enemies += Enemy(first.player.x, first.player.y, 0f, 0)
        first.update(day)
        assertEquals(GamePhase.GAME_OVER, first.phase)
        assertEquals(20_000L, profile.ghostDayKey)
        assertTrue(profile.ghostTrack.size >= 20)

        val second = startedEngine(profile, RunMode.DAILY, Rng(2L), day)
        repeat(30) { second.enemies.clear(); second.coins.clear(); second.powerups.clear(); second.update(day) }
        assertTrue(second.ghostVisible)
        assertEquals(second.player.x, second.ghostX, 0.5f)
    }

    @Test
    fun yesterdaysGhostStaysHome() {
        val day = 20_000L * GameConfig.DAY_MS
        val profile = PlayerProfile().apply {
            ghostDayKey = 19_999L
            ghostTrack = ShortArray(400)
        }
        val engine = startedEngine(profile, RunMode.DAILY, Rng(1L), day)
        repeat(30) { engine.enemies.clear(); engine.update(day) }
        assertTrue(!engine.ghostVisible)
    }

    @Test
    fun loginStreakPaysFromTheSecondDay() {
        assertEquals(0, Missions.streakReward(1))
        assertEquals(5, Missions.streakReward(2))
        assertEquals(60, Missions.streakReward(7))
        assertEquals(5, Missions.streakReward(8))
        assertEquals(60, Missions.streakReward(14))

        val profile = PlayerProfile()
        val engine = GameEngine(profile)
        engine.enterMenu(20_000L * GameConfig.DAY_MS)
        assertEquals(0, engine.takeStreakReward())
        engine.enterMenu(20_001L * GameConfig.DAY_MS)
        assertEquals(2, profile.dailyStreak)
        assertEquals(5, profile.totalCoins)
        assertEquals(5, engine.takeStreakReward())
        assertEquals(0, engine.takeStreakReward())
    }

    @Test
    fun leagueWeeksStartOnMonday() {
        // 1970-01-05 was the first Monday after the epoch; 2026-11-09 is a Monday too.
        assertEquals(0L, GameConfig.weekOf(3L))
        assertEquals(1L, GameConfig.weekOf(4L))
        val monday = 20_766L
        assertEquals(GameConfig.weekOf(monday - 1) + 1, GameConfig.weekOf(monday))
        assertEquals(GameConfig.weekOf(monday), GameConfig.weekOf(monday + 6))
    }

    @Test
    fun aNormalRunSetsThisWeeksLeagueBest() {
        val day = 20_401L * GameConfig.DAY_MS
        val profile = PlayerProfile().apply { weeklyKey = 1L; weeklyBest = 999 }
        val engine = startedEngine(profile, nowMs = day)
        engine.coins.clear()
        collectCoins(engine, 2)
        engine.enemies += Enemy(engine.player.x, engine.player.y, 0f, 0)
        engine.update(day)
        assertEquals(GameConfig.weekOf(20_401L), profile.weeklyKey)
        assertEquals(engine.score, profile.weeklyBest)
    }

    @Test
    fun aWeakerRunIsNeverSentForAProfile() {
        val p = PlayerProfile().apply { changeProfileNick("Ala") }
        assertTrue(!p.improvesBoards(0, null, 10L))
        assertTrue(p.improvesBoards(50, null, 10L))
        p.markSubmitted(50, null, 10L)
        assertTrue(!p.improvesBoards(50, null, 10L))
        assertTrue(!p.improvesBoards(49, null, 10L))
        assertTrue(p.improvesBoards(51, null, 10L))
    }

    @Test
    fun anAcceptedScoreNeverLowersWhatTheProfileHolds() {
        val p = PlayerProfile().apply { changeProfileNick("Ala") }
        p.markSubmitted(50, null, 10L)
        p.markSubmitted(20, null, 10L)
        assertEquals(50, p.submittedGlobal)
        assertEquals(50, p.submittedWeekly)
    }

    @Test
    fun aNewLeagueWeekStartsFromZeroButTheRecordStays() {
        val p = PlayerProfile().apply { changeProfileNick("Ala") }
        p.markSubmitted(50, null, 10L)
        assertTrue("a new week has nothing on it yet", p.improvesBoards(5, null, 11L))
        p.markSubmitted(5, null, 11L)
        assertEquals(50, p.submittedGlobal)
        assertEquals(5, p.submittedWeekly)
        assertTrue(!p.improvesBoards(5, null, 11L))
    }

    @Test
    fun theDailyBoardIsTrackedPerDay() {
        val p = PlayerProfile().apply { changeProfileNick("Ala") }
        p.markSubmitted(30, 500L, 10L)
        assertEquals(0, p.submittedGlobal)
        assertTrue(!p.improvesBoards(30, 500L, 10L))
        assertTrue("tomorrow's route starts empty", p.improvesBoards(1, 501L, 10L))
    }

    @Test
    fun aNewNicknameStartsWithNothingOnTheBoards() {
        val p = PlayerProfile().apply { changeProfileNick("Ala") }
        p.markSubmitted(50, null, 10L)
        p.changeProfileNick("Ala")
        assertEquals("same nickname keeps its scores", 50, p.submittedGlobal)
        p.changeProfileNick("Ola")
        assertEquals(0, p.submittedGlobal)
        assertTrue(p.improvesBoards(1, null, 10L))
    }

    @Test
    fun everyCarBodyOutlineIsWellFormed() {
        for (body in CarBodies.all) {
            val outline = body.outline ?: continue
            assertEquals(body.id, 0, outline.size % 2)
            assertTrue(body.id, outline.size >= 8)
            assertTrue(body.id, outline.all { it in -1f..1f })
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
    fun runSummaryCountsThisRunOnly() {
        val engine = startedEngine()
        engine.coins.clear()
        engine.coins += Coin(engine.player.x, engine.player.y - 10f, 0f)
        var guard = 0
        while (engine.coinsInSession == 0 && guard++ < 300) engine.update(0L)
        assertEquals(1, engine.bestStreakInRun)
        engine.startRun(0L)
        assertEquals(0, engine.bestStreakInRun)
        assertEquals(0, engine.nearMissesInRun)
        assertEquals(0, engine.powerupsInRun)
        assertEquals(0, engine.stage)
    }

    /** Plants coins on the parked player one by one and returns every event seen meanwhile. */
    private fun collectCoins(engine: GameEngine, count: Int): List<GameEvent> {
        val seen = ArrayList<GameEvent>()
        for (k in 1..count) {
            engine.coins += Coin(engine.player.x, engine.player.y - 10f, 0f)
            var guard = 0
            while (engine.coinsInSession < k && guard++ < 300) {
                engine.enemies.clear()
                engine.update(0L)
                seen += engine.events
            }
        }
        return seen
    }

    @Test
    fun reachingAComboTierIsAnnounced() {
        val engine = startedEngine()
        engine.coins.clear()
        val events = collectCoins(engine, GameConfig.COMBO_T1)
        assertEquals(listOf(GameEvent.ComboTierUp(1)), events.filterIsInstance<GameEvent.ComboTierUp>())
    }

    @Test
    fun newRecordIsAnnouncedOnlyWhenThereWasOneToBeat() {
        val fresh = startedEngine()
        fresh.coins.clear()
        assertTrue(GameEvent.NewHighScore !in collectCoins(fresh, 1))

        val veteran = startedEngine(PlayerProfile().apply { highScore = 1 })
        veteran.coins.clear()
        assertTrue(GameEvent.NewHighScore in collectCoins(veteran, 2))
    }

    @Test
    fun matchingTheRecordIsNotANewRecord() {
        val engine = startedEngine(PlayerProfile().apply { highScore = 1 })
        engine.coins.clear()
        collectCoins(engine, 1)
        assertEquals(1, engine.score)
        assertTrue(!engine.newRecord)
        collectCoins(engine, 2)
        assertTrue(engine.newRecord)
    }

    @Test
    fun dailyRouteScoreStaysOffTheAllTimeRecord() {
        val profile = PlayerProfile().apply { highScore = 1 }
        val engine = startedEngine(profile, RunMode.DAILY)
        engine.coins.clear()
        val events = collectCoins(engine, 3)
        assertEquals(1, profile.highScore)
        assertTrue(GameEvent.NewHighScore !in events)
        assertTrue(!engine.newRecord)
    }

    @Test
    fun dailyRouteTrafficDoesNotDependOnTheScore() {
        val day = 20_000L * GameConfig.DAY_MS
        val scoring = startedEngine(PlayerProfile(), RunMode.DAILY, Rng(1L), day)
        val idle = startedEngine(PlayerProfile(), RunMode.DAILY, Rng(2L), day)
        repeat(1_200) { tick ->
            if (scoring.phase == GamePhase.PLAYING && tick % 3 == 0) {
                scoring.coins += Coin(scoring.player.x, scoring.player.y - 10f, 0f)
            }
            if (scoring.phase == GamePhase.PLAYING) scoring.update(0L)
            if (idle.phase == GamePhase.PLAYING) idle.update(0L)
        }
        assertTrue("the run should have scored", scoring.score > idle.score + 40)
        assertEquals(
            idle.enemies.map { listOf(it.x, it.y, it.hue, it.kind) },
            scoring.enemies.map { listOf(it.x, it.y, it.hue, it.kind) },
        )
    }

    @Test
    fun comboMultiplierTiersMatchConfig() {
        assertEquals(1f, GameConfig.comboMultiplier(0), 0f)
        assertEquals(1.5f, GameConfig.comboMultiplier(GameConfig.COMBO_T1), 0f)
        assertEquals(2f, GameConfig.comboMultiplier(GameConfig.COMBO_T2), 0f)
        assertEquals(3f, GameConfig.comboMultiplier(GameConfig.COMBO_T3), 0f)
        assertEquals(4f, GameConfig.comboMultiplier(GameConfig.COMBO_T4), 0f)
        assertEquals(0, GameConfig.comboTier(GameConfig.COMBO_T1 - 1))
        assertEquals(1, GameConfig.comboTier(GameConfig.COMBO_T1))
        assertEquals(4, GameConfig.comboTier(GameConfig.COMBO_T4 + 5))
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
