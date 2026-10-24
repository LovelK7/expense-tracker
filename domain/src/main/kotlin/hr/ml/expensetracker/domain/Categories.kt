package hr.ml.expensetracker.domain

import java.util.Locale

object Categories {
    /**
     * Seed list: the legacy "Kategorije" tab plus every category used in "POPIS",
     * with "Odjeća" folded into "Odjeća i oprema".
     */
    val DEFAULT: List<String> = listOf(
        "Adaptacija",
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

    /** The most used legacy categories, most used first; the add form shows them before the rest. */
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

    /** Legacy-only: transfers are recorded as settlements from now on, so new expenses skip it. */
    const val LEGACY_TRANSFER = "Prijenos"

    /** Categories offered for a new expense: [MOST_USED] first, then the rest in list order. */
    val FOR_PICKER: List<String> = MOST_USED + (DEFAULT - MOST_USED.toSet() - LEGACY_TRANSFER)

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
