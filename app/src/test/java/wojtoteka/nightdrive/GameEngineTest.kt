package wojtoteka.nightdrive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import wojtoteka.nightdrive.domain.PlayerProfile
import wojtoteka.nightdrive.engine.Coin
import wojtoteka.nightdrive.engine.Enemy
import wojtoteka.nightdrive.engine.GameConfig
import wojtoteka.nightdrive.engine.GameEngine
import wojtoteka.nightdrive.engine.GameEvent
import wojtoteka.nightdrive.engine.GamePhase

/**
 * JVM-only sanity tests for the framework-agnostic engine: the core loop that broke would break
 * these first (coin pickup/award, crash -> game over, shield block). Objects are planted directly
 * in the public entity lists, so no rendering or Android pieces are involved.
 */
class GameEngineTest {

    /** Runs the intro until the car is on screen and the simulation is actually PLAYING. */
    private fun startedEngine(profile: PlayerProfile = PlayerProfile()): GameEngine {
        val engine = GameEngine(profile)
        engine.startRun(0L)
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
