package hr.ml.expensetracker.domain

import hr.ml.expensetracker.domain.Person.LOVEL
import hr.ml.expensetracker.domain.Person.MARINA
import hr.ml.expensetracker.domain.SplitType.FULL_FOR_OTHER
import hr.ml.expensetracker.domain.SplitType.HALF
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.assertEquals

class StatisticsTest {
    private fun d(m: Int, day: Int, y: Int = 2026) = LocalDate.of(y, m, day)

    private val expenses = listOf(
        expense(1000, MARINA, HALF, d(1, 1), "Hrana"),
        expense(3000, LOVEL, HALF, d(1, 31), "Režije"),
        expense(501, LOVEL, HALF, d(2, 1), "Hrana"),
        expense(2000, MARINA, FULL_FOR_OTHER, d(2, 28), "Poklon"), // Marina paid for Lovel
        expense(700, LOVEL, FULL_FOR_OTHER, d(3, 1), "Zdravlje"), // Lovel paid for Marina
        expense(9999, LOVEL, HALF, d(2, 10), "Hrana", deleted = true),
        expense(4000, MARINA, HALF, d(12, 31, 2025), "Hrana"),
    )

    @Test
    fun `totals per category, largest first`() {
        val s = Statistics.compute(expenses, d(1, 1), d(3, 31))
        assertEquals(mapOf("Režije" to 3000L, "Poklon" to 2000L, "Hrana" to 1501L, "Zdravlje" to 700L), s.byCategory)
        assertEquals(listOf("Režije", "Poklon", "Hrana", "Zdravlje"), s.byCategory.keys.toList())
        assertEquals(7201, s.totalCents)
    }

    @Test
    fun `totals per month, chronological`() {
        val s = Statistics.compute(expenses, d(12, 1, 2025), d(3, 31))
        assertEquals(
            listOf(YearMonth.of(2025, 12) to 4000L, YearMonth.of(2026, 1) to 4000L, YearMonth.of(2026, 2) to 2501L, YearMonth.of(2026, 3) to 700L),
            s.byMonth.toList(),
        )
    }

    @Test
    fun `range is inclusive and excludes deleted rows`() {
        val feb = Statistics.forMonth(expenses, YearMonth.of(2026, 2))
        assertEquals(2501, feb.totalCents) // 501 + 2000, the deleted 9999 is ignored
        assertEquals(d(2, 1), feb.from)
        assertEquals(d(2, 28), feb.to)
    }

    @Test
    fun `per-person share - half of HALF plus FULL_FOR_OTHER paid on their behalf`() {
        val s = Statistics.forYear(expenses, 2026)
        // HALF total 4501 -> Lovel 2250 (2250.5 half-even), Marina 2251
        assertEquals(2250 + 2000L, s.shareByPerson[LOVEL])
        assertEquals(2251 + 700L, s.shareByPerson[MARINA])
        assertEquals(s.totalCents, s.shareByPerson.values.sum())
    }

    @Test
    fun `paid per person`() {
        val s = Statistics.forYear(expenses, 2026)
        assertEquals(3000L + 501 + 700, s.paidByPerson[LOVEL])
        assertEquals(1000L + 2000, s.paidByPerson[MARINA])
    }

    @Test
    fun `empty range gives zeros`() {
        val s = Statistics.forYear(expenses, 2020)
        assertEquals(0, s.totalCents)
        assertEquals(emptyMap(), s.byCategory)
        assertEquals(mapOf(LOVEL to 0L, MARINA to 0L), s.shareByPerson)
    }

    @Test
    fun `reversed range is rejected`() {
        assertThrows<IllegalArgumentException> { Statistics.compute(expenses, d(2, 1), d(1, 1)) }
    }
    @Test
    fun `expense count excludes deleted rows and rows outside the range`() {
        assertEquals(2, Statistics.forMonth(expenses, YearMonth.of(2026, 2)).expenseCount)
        assertEquals(5, Statistics.forYear(expenses, 2026).expenseCount)
        assertEquals(0, Statistics.forYear(expenses, 2020).expenseCount)
    }

    @Test
    fun `continuous months fill gaps with zero`() {
        val gappy = listOf(
            expense(100, LOVEL, HALF, d(11, 5, 2025)),
            expense(300, MARINA, HALF, d(2, 14)),
            expense(50, MARINA, HALF, d(2, 15)),
        )
        val s = Statistics.compute(gappy, d(1, 1, 2025), d(12, 31))
        assertEquals(
            listOf(
                YearMonth.of(2025, 11) to 100L,
                YearMonth.of(2025, 12) to 0L,
                YearMonth.of(2026, 1) to 0L,
                YearMonth.of(2026, 2) to 350L,
            ),
            s.byMonthContinuous().toList(),
        )
        assertEquals(mapOf(YearMonth.of(2025, 11) to 100L, YearMonth.of(2026, 2) to 350L), s.byMonth)
    }

    @Test
    fun `continuous months are empty without expenses`() {
        assertEquals(emptyMap(), Statistics.forYear(expenses, 2020).byMonthContinuous())
    }

    @Test
    fun `all time spans the earliest to the latest live expense`() {
        val withLateDeleted = expenses + expense(5, LOVEL, HALF, d(6, 1, 2027), deleted = true)
        val s = Statistics.forAll(withLateDeleted)!!
        assertEquals(d(12, 31, 2025), s.from)
        assertEquals(d(3, 1), s.to) // the deleted 2027 row does not stretch the range
        assertEquals(7201L + 4000, s.totalCents)
        assertEquals(6, s.expenseCount)
        assertEquals(s.totalCents, s.shareByPerson.values.sum())
    }

    @Test
    fun `all time is null without live expenses`() {
        assertEquals(null, Statistics.forAll(emptyList()))
        assertEquals(null, Statistics.forAll(listOf(expense(100, LOVEL, deleted = true))))
    }
}
