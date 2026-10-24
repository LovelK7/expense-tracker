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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrendsTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)
    private fun ym(y: Int, m: Int) = YearMonth.of(y, m)
    private val oct = ym(2026, 10)
    private val today = d(2026, 10, 24)

    // ---- Percent ----

    @Test
    fun `percent change is rounded half-even`() {
        assertEquals(12, Percent.change(11_200, 10_000))
        assertEquals(-5, Percent.change(9_500, 10_000))
        assertEquals(0, Percent.change(10_000, 10_000))
        assertEquals(-100, Percent.change(0, 10_000))
        assertEquals(200, Percent.change(300, 100))
        // 12.5 -> 12 (even), 13.5 -> 14 (even), -12.5 -> -12, 12.51 -> 13.
        assertEquals(12, Percent.change(1_125, 1_000))
        assertEquals(14, Percent.change(1_135, 1_000))
        assertEquals(-12, Percent.change(875, 1_000))
        assertEquals(13, Percent.change(112_510, 100_000))
        assertNull(Percent.change(500, 0))
    }

    @Test
    fun `percent of a whole is rounded half-even`() {
        assertEquals(38, Percent.of(3_800, 10_000))
        assertEquals(50, Percent.of(1, 2))
        assertEquals(0, Percent.of(1, 200)) // 0.5 -> 0
        assertEquals(2, Percent.of(3, 200)) // 1.5 -> 2
        assertEquals(2, Percent.of(5, 200)) // 2.5 -> 2
        assertEquals(33, Percent.of(1, 3))
        assertEquals(67, Percent.of(2, 3))
        assertNull(Percent.of(1, 0))
    }

    @Test
    fun `percent display uses a non-breaking space and a real minus sign`() {
        assertEquals("+12 %", Percent.formatChange(12))
        assertEquals("−5 %", Percent.formatChange(-5))
        assertEquals("0 %", Percent.formatChange(0))
        assertEquals("38 %", Percent.format(38))
        assertEquals("−7 %", Percent.format(-7))
        assertFalse('-' in Percent.formatChange(-5))
    }

    @Test
    fun `half-even division`() {
        assertEquals(2, divideHalfEven(5, 2))
        assertEquals(4, divideHalfEven(7, 2))
        assertEquals(-2, divideHalfEven(-5, 2))
        assertEquals(-4, divideHalfEven(-7, 2))
        assertEquals(3, divideHalfEven(10, 3))
        assertEquals(-3, divideHalfEven(-10, 3))
        assertEquals(-4, divideHalfEven(-11, 3))
        assertThrows<IllegalArgumentException> { divideHalfEven(1, 0) }
    }

    // ---- Comparisons ----

    private val history = listOf(
        expense(10_000, LOVEL, HALF, d(2025, 10, 3)), // October last year
        expense(20_000, MARINA, HALF, d(2026, 9, 30)), // September
        expense(11_200, LOVEL, HALF, d(2026, 10, 1)),
        expense(11_200, MARINA, HALF, d(2026, 10, 31)),
        expense(99_999, LOVEL, FULL_FOR_OTHER, d(2026, 9, 15), Categories.TRANSFER),
        expense(88_888, LOVEL, HALF, d(2026, 9, 16), deleted = true),
    )

    @Test
    fun `month against the previous month and the same month a year earlier`() {
        val prev = Trends.previousMonth(history, oct)
        assertEquals(d(2026, 9, 1)..d(2026, 9, 30), prev.reference)
        assertEquals(22_400, prev.currentCents)
        assertEquals(20_000, prev.referenceCents) // transfer and deleted row left out
        assertEquals(1, prev.referenceCount)
        assertEquals(12, prev.changePercent)
        assertFalse(prev.yearToDate)

        val lastYear = Trends.sameMonthLastYear(history, oct)
        assertEquals(d(2025, 10, 1)..d(2025, 10, 31), lastYear.reference)
        assertEquals(124, lastYear.changePercent)
    }

    @Test
    fun `no reference data means no percentage`() {
        assertNull(Trends.previousMonth(history, ym(2025, 10)).changePercent)
        // A reference with only a transfer has no spending either.
        val onlyTransfer = listOf(
            expense(5_000, LOVEL, FULL_FOR_OTHER, d(2026, 9, 1), Categories.TRANSFER),
            expense(1_000, LOVEL, HALF, d(2026, 10, 1)),
        )
        val c = Trends.previousMonth(onlyTransfer, oct)
        assertEquals(0, c.referenceCount)
        assertNull(c.changePercent)
        // A reference with data but nothing in the current period: -100 %.
        assertEquals(-100, Trends.previousMonth(history, ym(2026, 11)).changePercent)
    }

    @Test
    fun `comparisons follow the person filter`() {
        val lovel = Trends.previousMonth(history, oct, LOVEL)
        assertEquals(11_200, lovel.currentCents)
        assertEquals(0, lovel.referenceCount) // Lovel's September is only the transfer
        assertNull(lovel.changePercent)
        val marina = Trends.previousMonth(history, oct, MARINA)
        assertEquals(-44, marina.changePercent) // 11200 vs 20000
    }

    @Test
    fun `the current year is compared year-to-date`() {
        val list = listOf(
            expense(1_000, LOVEL, HALF, d(2025, 3, 1)),
            expense(500, LOVEL, HALF, d(2025, 10, 24)), // the same day a year ago: included
            expense(9_000, LOVEL, HALF, d(2025, 10, 25)), // after it: not part of "do danas"
            expense(1_800, MARINA, HALF, d(2026, 2, 1)),
            expense(4_000, MARINA, HALF, d(2026, 12, 1)), // dated ahead of today
        )
        val c = Trends.previousYear(list, 2026, today)
        assertTrue(c.yearToDate)
        assertEquals(d(2026, 1, 1)..today, c.current)
        assertEquals(d(2025, 1, 1)..d(2025, 10, 24), c.reference)
        assertEquals(1_800, c.currentCents)
        assertEquals(1_500, c.referenceCents)
        assertEquals(20, c.changePercent)

        // A past year is compared in full.
        val past = Trends.previousYear(list, 2025, today)
        assertFalse(past.yearToDate)
        assertEquals(d(2025, 1, 1)..d(2025, 12, 31), past.current)
        assertEquals(d(2024, 1, 1)..d(2024, 12, 31), past.reference)
        assertEquals(10_500, past.currentCents)
        assertNull(past.changePercent) // nothing in 2024
    }

    @Test
    fun `year-to-date on 29 February compares with 28 February`() {
        val c = Trends.previousYear(emptyList(), 2028, d(2028, 2, 29))
        assertEquals(d(2027, 1, 1)..d(2027, 2, 28), c.reference)
    }

    // ---- Monthly series and category trend ----

    @Test
    fun `monthly series covers the window ending with the given month`() {
        val list = listOf(
            expense(100, LOVEL, HALF, d(2025, 10, 31), "Hrana"), // just before the window
            expense(200, LOVEL, HALF, d(2025, 11, 1), "Hrana"),
            expense(300, MARINA, HALF, d(2026, 3, 15), "Hrana"),
            expense(400, MARINA, HALF, d(2026, 3, 16), "Režije"),
            expense(500, LOVEL, HALF, d(2026, 10, 31), "Hrana"),
            expense(600, LOVEL, HALF, d(2026, 11, 1), "Hrana"), // after it
            expense(700, LOVEL, FULL_FOR_OTHER, d(2026, 5, 1), Categories.TRANSFER),
        )
        val series = Trends.monthlySeries(list, oct)
        assertEquals(12, series.size)
        assertEquals((0L..11L).map { ym(2025, 11).plusMonths(it) }, series.map { it.month })
        assertEquals(200, series.first().cents)
        assertEquals(700, series.single { it.month == ym(2026, 3) }.cents)
        assertEquals(0, series.single { it.month == ym(2026, 5) }.cents) // the transfer is not spending
        assertEquals(500, series.last().cents)

        val hrana = Trends.monthlySeries(list, oct, category = "Hrana")
        assertEquals(300, hrana.single { it.month == ym(2026, 3) }.cents)
        assertEquals(1_000, hrana.sumOf { it.cents })
        val hranaMarina = Trends.monthlySeries(list, oct, category = "Hrana", person = MARINA)
        assertEquals(300, hranaMarina.sumOf { it.cents })
        assertEquals(listOf(MonthTotal(ym(2026, 10), 500)), Trends.monthlySeries(list, oct, months = 1))
    }

    @Test
    fun `category trend averages over the window's months with data`() {
        val full = (0L until 24L).map { expense(1_000 + it, LOVEL, HALF, d(2024, 11, 5).plusMonths(it), "Hrana") }
        val t = Trends.categoryTrend(full, "Hrana", oct)
        assertEquals(12, t.months.size)
        assertEquals(12, t.averagedMonths)
        // Months Nov 2025..Oct 2026 hold 1012..1023: average 1017.5 -> 1018 (even).
        assertEquals(1_018, t.monthlyAverageCents)

        // History starting in August 2026: only Aug..Oct are averaged; empty categories still count them.
        val young = listOf(
            expense(3_000, LOVEL, HALF, d(2026, 8, 1), "Režije"),
            expense(1_000, LOVEL, HALF, d(2026, 9, 1), "Hrana"),
            expense(2_001, MARINA, HALF, d(2026, 10, 1), "Hrana"),
        )
        val hrana = Trends.categoryTrend(young, "Hrana", oct)
        assertEquals(3, hrana.averagedMonths)
        assertEquals(1_000, hrana.monthlyAverageCents) // 3001 / 3 = 1000.33
        val marina = Trends.categoryTrend(young, "Hrana", oct, MARINA)
        assertEquals(1, marina.averagedMonths) // Marina's first spending month is October
        assertEquals(2_001, marina.monthlyAverageCents)

        val none = Trends.categoryTrend(emptyList(), "Hrana", oct)
        assertEquals(0, none.averagedMonths)
        assertEquals(0, none.monthlyAverageCents)
        assertEquals(12, none.months.size)
    }

    // ---- Years ----

    @Test
    fun `year rows newest first with monthly averages`() {
        val list = listOf(
            expense(12_000, LOVEL, HALF, d(2023, 3, 10)), // history starts in March 2023
            expense(24_000, MARINA, HALF, d(2024, 6, 1)),
            expense(10_001, LOVEL, HALF, d(2026, 2, 1)),
            expense(5_000, LOVEL, FULL_FOR_OTHER, d(2026, 3, 1), Categories.TRANSFER),
            expense(1, LOVEL, HALF, d(2026, 4, 1), deleted = true),
        )
        val years = Trends.byYear(list, today)
        assertEquals(listOf(2026, 2025, 2024, 2023), years.map { it.year })
        assertEquals(listOf(10_001L, 0L, 24_000L, 12_000L), years.map { it.cents })
        assertEquals(listOf(1, 0, 1, 1), years.map { it.expenseCount })
        // 2026: January..October (today); 2023: March..December.
        assertEquals(listOf(10, 12, 12, 10), years.map { it.months })
        assertEquals(listOf(1_000L, 0L, 2_000L, 1_200L), years.map { it.monthlyAverageCents })
    }

    @Test
    fun `year rows follow the person filter and future-dated expenses`() {
        val list = listOf(
            expense(1_000, LOVEL, HALF, d(2025, 1, 1)),
            expense(1_200, MARINA, HALF, d(2026, 1, 1)),
            expense(6_000, MARINA, HALF, d(2026, 12, 1)), // ahead of today: the average runs to December
        )
        val marina = Trends.byYear(list, today, MARINA)
        assertEquals(listOf(2026), marina.map { it.year })
        assertEquals(12, marina.single().months)
        assertEquals(600, marina.single().monthlyAverageCents)
        val both = Trends.byYear(list, today)
        assertEquals(listOf(2026, 2025), both.map { it.year })
        assertEquals(listOf(7_200L, 1_000L), both.map { it.cents })
        assertEquals(emptyList(), Trends.byYear(emptyList(), today))
    }

    @Test
    fun `first spending month ignores transfers and deleted rows`() {
        val list = listOf(
            expense(1, LOVEL, HALF, d(2022, 1, 1), deleted = true),
            expense(1, LOVEL, FULL_FOR_OTHER, d(2022, 6, 1), Categories.TRANSFER),
            expense(1, MARINA, HALF, d(2023, 2, 1)),
            expense(1, LOVEL, HALF, d(2023, 5, 1)),
        )
        assertEquals(ym(2023, 2), Trends.firstSpendingMonth(list))
        assertEquals(ym(2023, 5), Trends.firstSpendingMonth(list, LOVEL))
        assertNull(Trends.firstSpendingMonth(emptyList()))
    }
}
