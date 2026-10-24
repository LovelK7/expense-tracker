package hr.ml.expensetracker.ui.common

/** What an amount field may hold while the user types: euros, then optionally a comma and 0–2 decimals. */
private val AMOUNT_TYPING = Regex("""\d{0,9}(,\d{0,2})?""")

/**
 * Filters a keystroke in an amount field. A dot becomes a comma (the field always shows
 * "12,50"); returns the text to show, or null to keep the previous value (the keystroke is ignored).
 */
fun acceptAmountInput(text: String): String? {
    val normalized = text.replace('.', ',').filterNot { it.isWhitespace() }
    return normalized.takeIf { AMOUNT_TYPING.matches(it) }
}
