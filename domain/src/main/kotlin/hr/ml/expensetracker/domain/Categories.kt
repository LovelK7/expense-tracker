package hr.ml.expensetracker.domain

import java.util.Locale

object Categories {
    /**
     * Seed list: the legacy "Kategorije" tab plus every category used in "POPIS",
     * with "Odjeća" folded into "Odjeća i oprema".
     */
    val DEFAULT: List<String> = listOf(
        "Adaptacija",
        "Armus stan",
        "Banka",
        "Edukacija",
        "Gotovina",
        "Hrana",
        "Hrana van",
        "Kredit",
        "MPO",
        "Odjeća i oprema",
        "Osiguranje",
        "Ostalo",
        "Poklon",
        "Potrepštine",
        "Prijenos",
        "Prijevoz",
        "Putovanja",
        "Režije",
        "Stanarina",
        "Usluge",
        "Zabava",
        "Zdravlje",
    )

    /** The most used legacy categories (at import time), most used first: the head of [FOR_PICKER]. */
    val MOST_USED: List<String> = listOf(
        "Hrana",
        "Prijevoz",
        "Hrana van",
        "Režije",
        "Potrepštine",
        "Putovanja",
        "MPO",
        "Adaptacija",
    )

    /**
     * Money moved between the two people (the legacy way of settling up), not consumption.
     * Statistics leave it out of every spending figure but the balance counts it like any expense.
     * Transfers are recorded as settlements from now on, so new expenses skip it.
     */
    const val TRANSFER = "Prijenos"

    /**
     * Categories offered for a new expense in their static order: [MOST_USED] first, then the rest
     * in list order. The add form shows them by real usage ([orderByUsage]); this order is the
     * fallback without data and the tie-break.
     */
    val FOR_PICKER: List<String> = MOST_USED + (DEFAULT - MOST_USED.toSet() - TRANSFER)

    /**
     * [categories] ordered by how many non-deleted [expenses] use each one (all time), most used
     * first. Ties, including categories nobody has used, keep their order in [categories], so with
     * no expenses the result is [categories] unchanged. Categories of [expenses] that are not in
     * [categories] (e.g. "Prijenos" for the new-expense picker) are not added.
     */
    fun orderByUsage(expenses: List<Expense>, categories: List<String> = FOR_PICKER): List<String> {
        if (expenses.isEmpty()) return categories
        val counts = expenses.filterNot { it.deleted }.groupingBy { it.category }.eachCount()
        // sortedByDescending is stable: equal counts stay in the order of [categories].
        return categories.sortedByDescending { counts[it] ?: 0 }
    }

    /** Legacy names that map onto a different canonical name. Keys are matched case-insensitively. */
    val DEFAULT_ALIASES: Map<String, String> = mapOf("Odjeća" to "Odjeća i oprema")
}

/**
 * Maps free-form category input onto the controlled list: trims, collapses inner whitespace and
 * matches case-insensitively ("hrana" → "Hrana"); applies aliases ("Odjeća" → "Odjeća i oprema").
 * Unknown categories are returned trimmed but otherwise unchanged.
 */
class CategoryNormalizer(
    categories: List<String> = Categories.DEFAULT,
    aliases: Map<String, String> = Categories.DEFAULT_ALIASES,
) {
    private val byKey: Map<String, String> = buildMap {
        categories.forEach { put(key(it), it.cleaned()) }
        aliases.forEach { (alias, target) -> put(key(alias), target.cleaned()) }
    }

    fun normalize(raw: String): String = byKey[key(raw)] ?: raw.cleaned()

    fun isKnown(raw: String): Boolean = key(raw) in byKey

    private fun key(s: String) = s.cleaned().lowercase(Locale.ROOT)

    private fun String.cleaned() = trim().replace(WHITESPACE, " ")

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}
