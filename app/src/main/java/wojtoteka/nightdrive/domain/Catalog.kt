package wojtoteka.nightdrive.domain

/** A purchasable car skin. Colours are ARGB longs so the engine/renderer stay Android-free. */
data class Skin(
    val id: String,
    val nameKey: String,
    val price: Int,
    val bodyColor: Long,   // 0xFFRRGGBB
    val accentColor: Long, // darker/secondary body colour
)

object Skins {
    const val DEFAULT_ID = "classic"

    val all: List<Skin> = listOf(
        Skin("classic", "skin_classic", 0, 0xFFF3F5FF, 0xFF9AA0B8),
        Skin("neon", "skin_neon", 150, 0xFF00E5FF, 0xFF0A8C93),
        Skin("rose", "skin_rose", 300, 0xFFFF6FB4, 0xFFA6466E),
        Skin("gold", "skin_gold", 500, 0xFFFFD23F, 0xFFB08A00),
        Skin("violet", "skin_violet", 750, 0xFF9B6BFF, 0xFF5B3CA6),
        Skin("ember", "skin_ember", 1100, 0xFFFF7A3D, 0xFFA8431A),
        Skin("chrome", "skin_chrome", 1500, 0xFFD9DEE8, 0xFF6E7488),
        Skin("toxic", "skin_toxic", 2000, 0xFFB6FF3D, 0xFF5C9B12),
    )

    fun byId(id: String): Skin = all.firstOrNull { it.id == id } ?: all.first()
}

enum class AchievementKind {
    TOTAL_COINS, SCORE, STREAK, TIME, DAILY_STREAK,
    SKIN_COUNT,        // skins owned (incl. the free default)
    SHIELD_BLOCK,      // lifetime collisions blocked by the shield
    BOOST_USED,        // lifetime starter boosts consumed at run start
    POWERUP_COLLECT,   // lifetime power-ups picked up on the road
}

data class Achievement(
    val id: String,
    val nameKey: String,
    val descKey: String,
    val goal: Int,
    val kind: AchievementKind,
    val reward: Int,
)

object Achievements {
    val all: List<Achievement> = listOf(
        Achievement("coins100", "ach_coins100", "ach_coins100_d", 100, AchievementKind.TOTAL_COINS, 15),
        Achievement("coins1000", "ach_coins1000", "ach_coins1000_d", 1000, AchievementKind.TOTAL_COINS, 60),
        Achievement("coins5000", "ach_coins5000", "ach_coins5000_d", 5000, AchievementKind.TOTAL_COINS, 200),
        Achievement("score50", "ach_score50", "ach_score50_d", 50, AchievementKind.SCORE, 15),
        Achievement("score100", "ach_score100", "ach_score100_d", 100, AchievementKind.SCORE, 40),
        Achievement("score250", "ach_score250", "ach_score250_d", 250, AchievementKind.SCORE, 100),
        Achievement("score500", "ach_score500", "ach_score500_d", 500, AchievementKind.SCORE, 200),
        Achievement("streak5", "ach_streak5", "ach_streak5_d", 5, AchievementKind.STREAK, 10),
        Achievement("streak12", "ach_streak12", "ach_streak12_d", 12, AchievementKind.STREAK, 35),
        Achievement("streak20", "ach_streak20", "ach_streak20_d", 20, AchievementKind.STREAK, 80),
        Achievement("survive60", "ach_survive60", "ach_survive60_d", 60, AchievementKind.TIME, 20),
        Achievement("survive120", "ach_survive120", "ach_survive120_d", 120, AchievementKind.TIME, 60),
        Achievement("survive300", "ach_survive300", "ach_survive300_d", 300, AchievementKind.TIME, 150),
        Achievement("daily7", "ach_daily7", "ach_daily7_d", 7, AchievementKind.DAILY_STREAK, 25),
        Achievement("daily30", "ach_daily30", "ach_daily30_d", 30, AchievementKind.DAILY_STREAK, 50),
        Achievement("daily100", "ach_daily100", "ach_daily100_d", 100, AchievementKind.DAILY_STREAK, 150),
        Achievement("skinsAll", "ach_skins_all", "ach_skins_all_d", Skins.all.size, AchievementKind.SKIN_COUNT, 250),
        Achievement("shield25", "ach_shield25", "ach_shield25_d", 25, AchievementKind.SHIELD_BLOCK, 75),
        Achievement("boosts25", "ach_boosts25", "ach_boosts25_d", 25, AchievementKind.BOOST_USED, 60),
        Achievement("powerups50", "ach_powerups50", "ach_powerups50_d", 50, AchievementKind.POWERUP_COLLECT, 50),
    )
}

/**
 * The five power-up kinds and their tuning. Durations in milliseconds (0 = instant). Each colour is
 * the token's ring colour on the road, so they're spread around the wheel and kept clear of the
 * coin's yellow.
 */
enum class PowerUpType(val id: String, val labelKey: String, val durationMs: Long, val color: Long) {
    SHIELD("shield", "pw_shield", 0L, 0xFF3BE1FF),
    MAGNET("magnet", "pw_magnet", 8_000L, 0xFFFF5A4F),
    SLOWMO("slowmo", "pw_slowmo", 5_000L, 0xFFB48CFF),
    DOUBLE("double", "pw_double", 10_000L, 0xFF5CFF8A),
    COIN_RUSH("coinrush", "pw_coinrush", 12_000L, 0xFFFF4FD8);

    companion object {
        val spawnable = entries.toList()
        fun fromId(id: String): PowerUpType = entries.first { it.id == id }
    }
}

/** Price of one shield charge in the shop, either bought outright or claimed via a rewarded ad. */
const val SHIELD_PRICE = 60

/** One-time permanent purchase: raises the shield charge cap (see [PlayerProfile.shieldMax]). */
const val SHIELD_CAPACITY_PRICE = 900

/**
 * Tiered permanent duration upgrades for the timed power-ups. Two ladders that stack additively:
 * one "all power-ups" ladder plus an individual ladder per power-up, each +20% / +50% / +100%.
 * (Global +50% and magnet +100% together mean the magnet runs +150% longer.)
 */
object BoostDurationUpgrade {
    const val MAX_LEVEL = 3
    private val percents = intArrayOf(0, 20, 50, 100)

    /** Price to buy the NEXT tier when currently at level index (0-based). */
    val tierPricesEach = intArrayOf(250, 600, 1200)
    val tierPricesAll = intArrayOf(800, 1800, 3500)

    fun percent(level: Int): Int = percents[level.coerceIn(0, MAX_LEVEL)]
    fun nextPriceEach(level: Int): Int? = tierPricesEach.getOrNull(level)
    fun nextPriceAll(level: Int): Int? = tierPricesAll.getOrNull(level)

    /** The four upgradable (timed) power-ups, in shop display order. */
    val upgradableTypes = listOf(PowerUpType.MAGNET, PowerUpType.SLOWMO, PowerUpType.DOUBLE, PowerUpType.COIN_RUSH)
}

/** A consumable purchase that runs one of the timed power-ups for a long 60s stretch of the next
 *  run (see GameConfig.STARTER_BOOST_MS) - slow motion from the very start of the run, the
 *  coin-earning three from the first collected coin. Bought in stock (each purchase adds one). */
data class Boost(val id: String, val nameKey: String, val descKey: String, val price: Int, val type: PowerUpType)

object Boosts {
    val all: List<Boost> = listOf(
        Boost("magnet_start", "boost_magnet", "boost_magnet_d", 40, PowerUpType.MAGNET),
        Boost("slowmo_start", "boost_slowmo", "boost_slowmo_d", 45, PowerUpType.SLOWMO),
        Boost("double_start", "boost_double", "boost_double_d", 55, PowerUpType.DOUBLE),
        Boost("coinrush_start", "boost_coinrush", "boost_coinrush_d", 70, PowerUpType.COIN_RUSH),
    )
}

/**
 * A purchasable colour scheme for the whole animated world (sky, sun, road, grid). Colours are
 * ARGB longs so the renderer stays Android-free; every slot maps 1:1 onto a constant that used
 * to be hard-coded in WorldRenderer, so the default theme is pixel-identical to the pre-theme look.
 */
data class WorldTheme(
    val id: String,
    val nameKey: String,
    val price: Int,
    val skyTop: Long,
    val skyBottom: Long,
    val star: Long,
    val sunGlow: Long,
    val sunTop: Long,
    val sunMid: Long,
    val sunLow: Long,
    val sunSlat: Long,
    val horizonGlow: Long,
    val roadTop: Long,
    val roadBottom: Long,
    val grid: Long,
    val horizonLine: Long,
)

object WorldThemes {
    const val DEFAULT_ID = "sunset"

    val all: List<WorldTheme> = listOf(
        WorldTheme(
            "sunset", "theme_sunset", 0,
            skyTop = 0xFF070818, skyBottom = 0xFF1A0C30, star = 0xFFEAF6FF,
            sunGlow = 0xFFFF4FA0, sunTop = 0xFFFFE9A8, sunMid = 0xFFFF8A3D, sunLow = 0xFFFF2E97,
            sunSlat = 0xFF120826,
            horizonGlow = 0xFFFF2E97, roadTop = 0xFF2A1146, roadBottom = 0xFF090512,
            grid = 0xFF7FE9FF, horizonLine = 0xFF37E7FF,
        ),
        WorldTheme(
            "aurora", "theme_aurora", 800,
            skyTop = 0xFF02101C, skyBottom = 0xFF0A2E33, star = 0xFFE8FBFF,
            sunGlow = 0xFF35E8B0, sunTop = 0xFFDFFFF2, sunMid = 0xFF52E8C0, sunLow = 0xFF10A87A,
            sunSlat = 0xFF06232A,
            horizonGlow = 0xFF35E8B0, roadTop = 0xFF0E2C3A, roadBottom = 0xFF04121A,
            grid = 0xFF7FFFD8, horizonLine = 0xFF4FF2C8,
        ),
        WorldTheme(
            "sakura", "theme_sakura", 800,
            skyTop = 0xFF160A24, skyBottom = 0xFF3A1233, star = 0xFFFFEFF7,
            sunGlow = 0xFFFF7AC8, sunTop = 0xFFFFF2E0, sunMid = 0xFFFFAAD4, sunLow = 0xFFEC4FA8,
            sunSlat = 0xFF260D2C,
            horizonGlow = 0xFFFF7AC8, roadTop = 0xFF3A1140, roadBottom = 0xFF10040F,
            grid = 0xFFFFB3DD, horizonLine = 0xFFFF8AD0,
        ),
        WorldTheme(
            "inferno", "theme_inferno", 1200,
            skyTop = 0xFF0E0406, skyBottom = 0xFF33080D, star = 0xFFFFE2CC,
            sunGlow = 0xFFFF4A2E, sunTop = 0xFFFFE28C, sunMid = 0xFFFF7A2E, sunLow = 0xFFE02222,
            sunSlat = 0xFF20060A,
            horizonGlow = 0xFFFF5A2E, roadTop = 0xFF360C12, roadBottom = 0xFF0C0305,
            grid = 0xFFFFA24A, horizonLine = 0xFFFF6A3D,
        ),
        WorldTheme(
            "ultraviolet", "theme_ultraviolet", 1000,
            skyTop = 0xFF0A0618, skyBottom = 0xFF241048, star = 0xFFF0EAFF,
            sunGlow = 0xFF9B6BFF, sunTop = 0xFFEDE4FF, sunMid = 0xFFB08CFF, sunLow = 0xFF6E3BFF,
            sunSlat = 0xFF160B2E,
            horizonGlow = 0xFF9B6BFF, roadTop = 0xFF221244, roadBottom = 0xFF07040F,
            grid = 0xFFC9A8FF, horizonLine = 0xFFAF7BFF,
        ),
        WorldTheme(
            "arctic", "theme_arctic", 1000,
            skyTop = 0xFF04101E, skyBottom = 0xFF0E3448, star = 0xFFEAFBFF,
            sunGlow = 0xFF4FC3FF, sunTop = 0xFFF0FBFF, sunMid = 0xFF8ADEFF, sunLow = 0xFF2E9BDF,
            sunSlat = 0xFF082234,
            horizonGlow = 0xFF4FC3FF, roadTop = 0xFF12334A, roadBottom = 0xFF050D14,
            grid = 0xFFA8E8FF, horizonLine = 0xFF6FD4FF,
        ),
    )

    fun byId(id: String): WorldTheme = all.firstOrNull { it.id == id } ?: all.first()
}
