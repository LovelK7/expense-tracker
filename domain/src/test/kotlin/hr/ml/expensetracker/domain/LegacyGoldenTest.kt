package hr.ml.expensetracker.domain

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.File
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Checks the balance engine against the real legacy history in `private/legacy_popis.csv`
 * (produced by `tools/export_legacy.py`, gitignored). Skipped when the file is absent, e.g. in CI.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LegacyGoldenTest {

    private lateinit var expenses: List<Expense>

    @BeforeAll
    fun load() {
        val csv = findRepoRoot()?.resolve("private/legacy_popis.csv")
        assumeTrue(csv != null && csv.isFile, "private/legacy_popis.csv not found; run tools/export_legacy.py")
        expenses = LegacyCsvParser.parse(csv!!.readText(Charsets.UTF_8), CategoryNormalizer())
    }

    @Test
    fun `row count matches the POPIS sheet`() {
        assertEquals(EXPECTED_ROWS, expenses.size)
    }

    @Test
    fun `balance reproduces the SALDO sheet`() {
        val balance = BalanceCalculator.balanceCents(expenses)
        // The SALDO sheet sums raw floats (some with sub-cent precision, e.g. fuel at 17.787 €)
        // and gets -534.7008 € -> -53470 cents. The app stores whole cents, so each of the ~50
        // sub-cent legacy rows is rounded half-up on export; that drift moves the exact result to
        // -53468.5 cents before the final half-even halving, i.e. -53469.
        assertEquals(EXPECTED_CENT_BALANCE, balance)
        assertTrue(abs(balance - SHEET_BALANCE_CENTS) <= 1, "balance $balance is more than 1 cent off the sheet")
    }

    @Test
    fun `every legacy category is on the controlled list`() {
        val normalizer = CategoryNormalizer()
        val unknown = expenses.map { it.category }.filterNot { normalizer.isKnown(it) }.toSet()
        assertEquals(emptySet(), unknown)
        assertEquals(385, expenses.count { it.category == "Hrana" }) // includes the one lowercase "hrana"
    }

    @Test
    fun `the add form's chips follow the legacy usage`() {
        assertEquals(
            listOf("Hrana", "Prijevoz", "Putovanja", "Režije", "Hrana van"),
            Categories.orderByUsage(expenses).take(5),
        )
    }

    @Test
    fun `statistics shares add up over the full history`() {
        val all = Statistics.compute(expenses, expenses.minOf { it.date }, expenses.maxOf { it.date })
        assertEquals(expenses.sumOf { it.amountCents }, all.totalCents)
        assertEquals(all.totalCents, all.shareByPerson.values.sum())
        assertEquals(all.totalCents, all.byCategory.values.sum())
        assertEquals(all.totalCents, all.byMonth.values.sum())
    }

    private fun findRepoRoot(): File? =
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }

    private companion object {
        const val EXPECTED_ROWS = 1069
        /** docs/DESIGN.md golden value: the SALDO sheet, -534.70 €. */
        const val SHEET_BALANCE_CENTS = -53470L
        /** Same history with per-row whole-cent amounts (what the app will store). */
        const val EXPECTED_CENT_BALANCE = -53469L
    }
}
