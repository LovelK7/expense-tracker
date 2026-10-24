package hr.ml.expensetracker.domain

import kotlin.math.absoluteValue

/** Croatian-locale display of EUR amounts held as cents, e.g. 53470 → "534,70 €". */
object Money {
    /** Non-breaking space between the number and the currency sign, so "€" never wraps alone. */
    const val NBSP = '\u00A0'

    fun format(cents: Long): String {
        val sign = if (cents < 0) "-" else ""
        require(cents != Long.MIN_VALUE) { "amount out of range" }
        val abs = cents.absoluteValue
        val euros = (abs / 100).toString().reversed().chunked(3).joinToString(".").reversed()
        val rest = (abs % 100).toString().padStart(2, '0')
        return "$sign$euros,$rest$NBSP€"
    }
}
