package hr.ml.expensetracker.domain

import kotlin.math.absoluteValue

/**
 * Croatian noun forms after a number, independent of the device locale (Android's `<plurals>`
 * follow the device language, which may not be Croatian):
 * 1, 21, 101 → [one] ("trošak"); 2–4, 22–24 → [few] ("troška"); 0, 5–20, 25 … → [many]
 * ("troškova"). The teens (11–14, 111–114) always take [many].
 */
object CroatianPlural {
    fun <T> choose(count: Long, one: T, few: T, many: T): T {
        val n = count.absoluteValue
        val lastTwo = n % 100
        return when {
            n % 10 == 1L && lastTwo != 11L -> one
            n % 10 in 2L..4L && lastTwo !in 12L..14L -> few
            else -> many
        }
    }
}
