package wojtoteka.nightdrive.domain

import kotlin.random.Random

/**
 * What a daily mission counts. [perRun] missions keep the best single run of the day (survive,
 * combo, score...); the others add up across every run of the day. [goals] holds the easy /
 * medium / hard target - the three missions of a day always come one of each.
 */
enum class MissionType(
    val id: String,
    val titleKey: String,
    val shortKey: String,
    val perRun: Boolean,
    val goals: IntArray,
) {
    COINS("coins", "mission_coins", "mission_coins_s", false, intArrayOf(25, 45, 70)),
    SURVIVE("survive", "mission_survive", "mission_survive_s", true, intArrayOf(40, 70, 110)),
    COMBO("combo", "mission_combo", "mission_combo_s", true, intArrayOf(6, 10, 15)),
    POWERUPS("powerups", "mission_powerups", "mission_powerups_s", false, intArrayOf(6, 10, 15)),
    NEAR_MISS("nearmiss", "mission_nearmiss", "mission_nearmiss_s", false, intArrayOf(5, 12, 20)),
    SCORE("score", "mission_score", "mission_score_s", true, intArrayOf(30, 60, 100)),
    /** Seconds survived in one run before the first collision the shield had to block. */
    CLEAN("clean", "mission_clean", "mission_clean_s", true, intArrayOf(30, 50, 80));

    companion object {
        fun fromId(id: String): MissionType? = entries.firstOrNull { it.id == id }
    }
}

/** One of the day's three missions. Mutated in place by the engine, like the rest of the profile. */
class DailyMission(
    val type: MissionType,
    val goal: Int,
    val reward: Int,
    var progress: Int = 0,
    var completed: Boolean = false,
)

object Missions {
    const val COUNT = 3
    private val rewards = intArrayOf(10, 15, 25)

    /** Extra coins for finishing all three missions of a day. */
    const val ALL_DONE_BONUS = 20

    /**
     * The missions of UTC day [dayKey]. Seeded from the day alone, so every player gets the same
     * three on the same day: easy, medium and hard, each of a different type.
     */
    fun forDay(dayKey: Long): MutableList<DailyMission> {
        val random = Random(dayKey * 7919L + 17L)
        return MissionType.entries.shuffled(random).take(COUNT)
            .mapIndexed { tier, type -> DailyMission(type, type.goals[tier], rewards[tier]) }
            .toMutableList()
    }

    /** Compact storage form: "type,goal,reward,progress,done" entries joined by ';'. */
    fun encode(missions: List<DailyMission>): String = missions.joinToString(";") {
        "${it.type.id},${it.goal},${it.reward},${it.progress},${if (it.completed) 1 else 0}"
    }

    /** Inverse of [encode]; unknown or malformed entries are dropped (the day then regenerates). */
    fun decode(raw: String?): MutableList<DailyMission> {
        if (raw.isNullOrBlank()) return mutableListOf()
        val out = raw.split(';').mapNotNull { entry ->
            val f = entry.split(',')
            if (f.size != 5) return@mapNotNull null
            val type = MissionType.fromId(f[0]) ?: return@mapNotNull null
            val goal = f[1].toIntOrNull() ?: return@mapNotNull null
            val reward = f[2].toIntOrNull() ?: return@mapNotNull null
            val progress = f[3].toIntOrNull() ?: return@mapNotNull null
            DailyMission(type, goal, reward, progress, f[4] == "1")
        }
        return if (out.size == COUNT) out.toMutableList() else mutableListOf()
    }
}
