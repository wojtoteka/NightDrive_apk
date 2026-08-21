package wojtoteka.nightdrive.engine

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.random.Random

/**
 * Thin wrapper over [Random] that reproduces the original engine's `random.intInRange`, including
 * its behaviour when MIN > MAX (used deliberately for enemy/coin spawn Y ranges).
 */
class Rng(seed: Long = Random.nextLong()) {
    private val random = Random(seed)

    fun next(): Float = random.nextFloat()

    /** floor(rand * (floor(max) - ceil(min) + 1) + ceil(min)) - matches the source exactly. */
    fun intInRange(min: Float, max: Float): Int {
        val lo = ceil(min)
        val hi = floor(max)
        return floor(random.nextFloat() * (hi - lo + 1f) + lo).toInt()
    }

    fun intInRange(min: Int, max: Int): Int = intInRange(min.toFloat(), max.toFloat())

    fun <T> pick(list: List<T>): T = list[random.nextInt(list.size)]

    fun nextFloat(from: Float, to: Float): Float = from + random.nextFloat() * (to - from)
}
