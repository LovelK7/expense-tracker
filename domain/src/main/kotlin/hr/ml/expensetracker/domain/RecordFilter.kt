package hr.ml.expensetracker.domain

import java.text.Normalizer
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/**
 * Text matching for search: case- and diacritics-insensitive ("rezije" finds "Režije",
 * "SPAR" finds "Spar Kastav"). Fold both sides with [fold] and compare the results.
 */
object SearchText {
    private val COMBINING_MARKS = Regex("\\p{M}+")
    private val WHITESPACE = Regex("\\s+")

    /**
     * [text] lower-cased without diacritics: NFD-decomposes it, drops the combining marks and maps
     * the letters that have no decomposition ("đ" → "d", also "ł", "ø", "ß" for good measure).
     */
    fun fold(text: String): String {
        val stripped = COMBINING_MARKS.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "")
        val lower = stripped.lowercase(Locale.ROOT)
        if (lower.none { it == 'đ' || it == 'ł' || it == 'ø' || it == 'ß' }) return lower
        return buildString(lower.length) {
            for (c in lower) {
                when (c) {
                    'đ' -> append('d')
                    'ł' -> append('l')
                    'ø' -> append('o')
                    'ß' -> append("ss")
                    else -> append(c)
                }
            }
        }
    }

    /** The folded search terms of a query: whitespace-separated words, blanks dropped. */
    fun terms(query: String): List<String> = fold(query).split(WHITESPACE).filter { it.isNotEmpty() }

    /** What a search looks at in an expense: its category and description, folded. */
    fun of(expense: Expense): String = fold("${expense.category} ${expense.description}")

    /** What a search looks at in a settlement: its note, folded. */
    fun of(settlement: Settlement): String = fold(settlement.note)
}

/**
 * The home list filter: an optional calendar [month] and the search [terms] (folded, see
 * [SearchText]). A record matches when its date is in the month (any date without one) and
 * every term occurs somewhere in its searchable text (category + description of an expense,
 * the note of a settlement).
 */
data class RecordFilter(val month: YearMonth? = null, val terms: List<String> = emptyList()) {
    /** False for the "show everything" filter. */
    val isActive: Boolean get() = month != null || terms.isNotEmpty()

    fun matchesMonth(date: LocalDate): Boolean = month == null || YearMonth.from(date) == month

    /** [folded] is the record's searchable text from [SearchText.of]; precompute it once per record. */
    fun matches(date: LocalDate, folded: String): Boolean = matchesMonth(date) && terms.all { it in folded }

    fun matches(expense: Expense): Boolean = !expense.deleted && matches(expense.date, SearchText.of(expense))

    fun matches(settlement: Settlement): Boolean =
        !settlement.deleted && matches(settlement.date, SearchText.of(settlement))

    companion object {
        val NONE = RecordFilter()

        /** A filter for [month] (null: all months) and the free-text [query]. */
        fun of(month: YearMonth? = null, query: String = ""): RecordFilter = RecordFilter(month, SearchText.terms(query))

        /** Every calendar month with at least one non-deleted expense or settlement, newest first. */
        fun months(expenses: Iterable<Expense>, settlements: Iterable<Settlement>): List<YearMonth> {
            val months = HashSet<YearMonth>()
            for (e in expenses) if (!e.deleted) months += YearMonth.from(e.date)
            for (s in settlements) if (!s.deleted) months += YearMonth.from(s.date)
            return months.sortedDescending()
        }
    }
}
