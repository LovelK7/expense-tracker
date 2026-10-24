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

    /**
     * Parses what a user typed into an amount field: euros with an optional comma or dot and at
     * most two decimals ("12", "12,5", "12.50", ",5"). Surrounding whitespace is ignored.
     * Returns the amount in cents, or null when the text is not such a number (empty, negative,
     * thousands separators, more than two decimals, absurdly large).
     */
    fun parseCents(input: String): Long? {
        val match = AMOUNT_INPUT.matchEntire(input.trim()) ?: return null
        val (euros, decimals) = match.destructured
        if (euros.isEmpty() && decimals.isEmpty()) return null
        return euros.ifEmpty { "0" }.toLong() * 100 + decimals.padEnd(2, '0').toLong()
    }

    /** [cents] as an editable amount-field value, e.g. 1250 → "12,50" (no grouping, no sign). */
    fun formatInput(cents: Long): String {
        require(cents >= 0) { "amount must not be negative: $cents" }
        return "${cents / 100},${(cents % 100).toString().padStart(2, '0')}"
    }

    /** Up to 9 euro digits (millions are plenty), then an optional separator and 0–2 decimals. */
    private val AMOUNT_INPUT = Regex("""(\d{0,9})(?:[.,](\d{0,2}))?""")
}
