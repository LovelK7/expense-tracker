package hr.ml.expensetracker.domain

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
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
        // Transfers ("Prijenos") are not spending: excluded from the totals, reported on the side.
        val transfers = expenses.filter { it.category == Categories.TRANSFER }
        assertTrue(transfers.isNotEmpty())
        assertEquals(transfers.sumOf { it.amountCents }, all.transferCents)
        assertEquals(transfers.size, all.transferCount)
        assertEquals(expenses.sumOf { it.amountCents }, all.totalCents + all.transferCents)
        assertEquals(expenses.size - transfers.size, all.expenseCount)
        assertTrue(Categories.TRANSFER !in all.byCategory)
        assertEquals(all.totalCents, all.shareByPerson.values.sum())
        assertEquals(all.totalCents, all.byCategory.values.sum())
        assertEquals(all.totalCents, all.byMonth.values.sum())
    }

    @Test
    fun `per-person views and the year rows add up to the couple's spending`() {
        val from = expenses.minOf { it.date }
        val to = expenses.maxOf { it.date }
        val all = Statistics.compute(expenses, from, to)
        val lovel = Statistics.compute(expenses, from, to, Person.LOVEL)
        val marina = Statistics.compute(expenses, from, to, Person.MARINA)
        assertEquals(all.totalCents, lovel.totalCents + marina.totalCents)
        assertEquals(all.expenseCount, lovel.expenseCount + marina.expenseCount)
        assertEquals(all.transferCents, lovel.transferCents + marina.transferCents)
        assertEquals(all.paidByPerson[Person.LOVEL], lovel.totalCents)

        val years = Trends.byYear(expenses, today = to)
        assertEquals(listOf(2026, 2025, 2024, 2023), years.map { it.year })
        assertEquals(all.totalCents, years.sumOf { it.cents })
        assertEquals(listOf(10, 12, 12, 12), years.map { it.months })
    }

    @Test
    fun `analytics over the full history add up to the spending total`() {
        val today = expenses.maxOf { it.date } // 2026-10-24
        val all = Statistics.compute(expenses, expenses.minOf { it.date }, today)
        val trends = Analytics.trends(expenses, AnalyticsRange.ALL, today)!!
        assertEquals(MonthWindow(YearMonth.of(2023, 1), YearMonth.of(2026, 10)), trends.window)
        assertEquals(46, trends.monthly.size)
        assertEquals(all.totalCents, trends.totalCents)
        assertEquals(all.totalCents, trends.categoryStack.columns.sumOf { it.totalCents })
        assertEquals(Analytics.STACK_CATEGORIES + 1, trends.categoryStack.categories.size)
        assertEquals(listOf(2023, 2024, 2025, 2026), trends.cumulativeByYear.map { it.year })
        assertEquals(all.totalCents, trends.cumulativeByYear.sumOf { it.totalCents })
        assertEquals(all.totalCents, trends.payers.paid.values.sumOf { s -> s.sumOf { it.cents } })
        assertEquals(all.totalCents, trends.payers.share.values.sumOf { s -> s.sumOf { it.cents } })

        val habits = Habits.habits(expenses, AnalyticsRange.ALL, today)!!
        assertEquals(all.expenseCount, habits.tiles.expenseCount)
        assertEquals(all.expenseCount, habits.histogram.sumOf { it.count })
        assertEquals(all.expenseCount, habits.activity.sumOf { it.count })
        assertEquals(all.totalCents, habits.weekdays.sumOf { it.cents })
        assertEquals(all.totalCents, habits.daysOfMonth.sumOf { it.cents })
        assertEquals(all.totalCents, habits.quarterStack.columns.sumOf { it.totalCents })
        // Hand-checked against private/legacy_popis.csv (tools-independent count in Python).
        assertEquals(2_743, habits.tiles.medianCents)
        assertEquals(listOf(129, 350, 247, 155, 97, 30, 29), habits.histogram.map { it.count })
        assertEquals(23, habits.activity.single { it.month == YearMonth.of(2025, 3) }.count)
        assertEquals(DayTotal(LocalDate.of(2024, 6, 7), 143_000), habits.tiles.busiestDay)
        assertEquals("Kaufland", habits.topDescriptions.first().label)
        assertEquals(88, habits.topDescriptions.first().count)

        val recent = Habits.habits(expenses, AnalyticsRange.MONTHS_24, today)!!
        assertEquals(519, recent.tiles.expenseCount)
        assertEquals(3_500, recent.tiles.medianCents)
        val calendarFrom = recent.calendar.first
        assertEquals(LocalDate.of(2025, 10, 25), calendarFrom)
        assertEquals(
            expenses.filter { Statistics.isSpending(it) && !it.date.isBefore(calendarFrom) }.sumOf { it.amountCents },
            recent.calendar.days.sum(),
        )
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
