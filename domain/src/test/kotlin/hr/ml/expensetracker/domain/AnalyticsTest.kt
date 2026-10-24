package hr.ml.expensetracker.domain

import hr.ml.expensetracker.domain.Person.LOVEL
import hr.ml.expensetracker.domain.Person.MARINA
import hr.ml.expensetracker.domain.SplitType.FULL_FOR_OTHER
import hr.ml.expensetracker.domain.SplitType.HALF
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnalyticsTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)
    private fun ym(y: Int, m: Int) = YearMonth.of(y, m)
    private val today = d(2026, 10, 24)

    private fun spend(cents: Long, date: LocalDate, category: String = "Hrana", paidBy: Person = LOVEL, split: SplitType = HALF) =
        expense(cents, paidBy, split, date, category)

    // ---- window ----

    @Test
    fun `the window ends with the current month and is clipped to the first data month`() {
        val data = listOf(spend(100, d(2025, 3, 5)), spend(100, d(2026, 9, 1)))
        assertEquals(MonthWindow(ym(2025, 11), ym(2026, 10)), Analytics.window(data, AnalyticsRange.MONTHS_12, today))
        // 24 months would start in November 2024; the data starts in March 2025.
        assertEquals(MonthWindow(ym(2025, 3), ym(2026, 10)), Analytics.window(data, AnalyticsRange.MONTHS_24, today))
        assertEquals(MonthWindow(ym(2025, 3), ym(2026, 10)), Analytics.window(data, AnalyticsRange.ALL, today))
        assertNull(Analytics.window(emptyList(), AnalyticsRange.ALL, today))
        assertNull(Analytics.window(listOf(spend(100, d(2026, 1, 1)).copy(deleted = true)), AnalyticsRange.ALL, today))
    }

    @Test
    fun `the window reaches a future-dated expense and counts transfers for its range`() {
        val data = listOf(spend(100, d(2026, 12, 1)), spend(100, d(2024, 1, 2), category = Categories.TRANSFER))
        val window = Analytics.window(data, AnalyticsRange.ALL, today)!!
        assertEquals(MonthWindow(ym(2024, 1), ym(2026, 12)), window)
        assertEquals(36, window.size)
        assertEquals(ym(2025, 1), window.months[12])
        assertEquals(12, window.indexOf(ym(2025, 1)))
        assertTrue(d(2026, 12, 31) in window)
        assertTrue(d(2027, 1, 1) !in window)
    }

    // ---- monthly series and rolling average ----

    @Test
    fun `monthly series is zero-filled and leaves out transfers, deleted rows and the other payer`() {
        val window = MonthWindow(ym(2026, 7), ym(2026, 10))
        val data = listOf(
            spend(1_000, d(2026, 7, 3)),
            spend(500, d(2026, 7, 30), paidBy = MARINA),
            spend(9_999, d(2026, 8, 3), category = Categories.TRANSFER),
            spend(7_777, d(2026, 8, 4)).copy(deleted = true),
            spend(2_000, d(2026, 10, 1)),
            spend(3_000, d(2026, 6, 30)), // before the window
        )
        val both = Analytics.trends(data, window)
        assertEquals(listOf(1_500L, 0L, 0L, 2_000L), both.monthly.map { it.cents })
        assertEquals(window.months, both.monthly.map { it.month })
        assertEquals(3_500, both.totalCents)
        val marina = Analytics.trends(data, window, MARINA)
        assertEquals(listOf(500L, 0L, 0L, 0L), marina.monthly.map { it.cents })
    }

    @Test
    fun `rolling average uses earlier months of the history but not months before the first spending`() {
        val data = listOf(
            spend(300, d(2026, 5, 1)),
            spend(600, d(2026, 6, 1)),
            spend(900, d(2026, 7, 1)),
            spend(0, d(2026, 8, 1)),
            spend(101, d(2026, 9, 1)),
        )
        val window = MonthWindow(ym(2026, 7), ym(2026, 9))
        val rolling = Analytics.trends(data, window).rollingAverage
        // July: (300 + 600 + 900) / 3; August: (600 + 900 + 0) / 3; September: (900 + 0 + 101) / 3 = 333.67 -> 334.
        assertEquals(listOf(600L, 500L, 334L), rolling.map { it.cents })
        assertEquals(window.months, rolling.map { it.month })

        // The history starts in May: the May average is May alone, June's is (300 + 600) / 2.
        val fromStart = Analytics.trends(data, MonthWindow(ym(2026, 4), ym(2026, 6))).rollingAverage
        assertEquals(listOf(ym(2026, 5), ym(2026, 6)), fromStart.map { it.month })
        assertEquals(listOf(300L, 450L), fromStart.map { it.cents })
    }

    @Test
    fun `rolling average rounds half-even`() {
        val data = listOf(spend(1, d(2026, 1, 1)), spend(2, d(2026, 2, 1)))
        // February: (1 + 2) / 2 = 1.5 -> 2 (even).
        assertEquals(listOf(1L, 2L), Analytics.trends(data, MonthWindow(ym(2026, 1), ym(2026, 2))).rollingAverage.map { it.cents })
        val even = listOf(spend(1, d(2026, 1, 1)), spend(4, d(2026, 2, 1)))
        // (1 + 4) / 2 = 2.5 -> 2 (even).
        assertEquals(listOf(1L, 2L), Analytics.trends(even, MonthWindow(ym(2026, 1), ym(2026, 2))).rollingAverage.map { it.cents })
    }

    // ---- stacked categories ----

    @Test
    fun `the stack keeps the top categories and folds the rest and the real Ostalo into Ostalo`() {
        val window = MonthWindow(ym(2026, 9), ym(2026, 10))
        val data = listOf(
            spend(7_000, d(2026, 9, 1), "A"),
            spend(6_000, d(2026, 9, 1), "B"),
            spend(5_000, d(2026, 10, 1), "C"),
            spend(4_000, d(2026, 10, 1), "D"),
            spend(3_000, d(2026, 10, 1), "E"),
            spend(2_000, d(2026, 10, 1), "F"),
            spend(1_000, d(2026, 9, 1), "G"),
            spend(9_000, d(2026, 9, 2), Analytics.OTHER), // largest, but always folded
            spend(500, d(2026, 10, 2), "H"),
        )
        val stack = Analytics.trends(data, window).categoryStack
        assertEquals(listOf("A", "B", "C", "D", "E", "F", Analytics.OTHER), stack.categories)
        assertEquals(listOf(ym(2026, 9), ym(2026, 10)), stack.columns.map { it.period })
        assertEquals(listOf(7_000L, 6_000L, 0L, 0L, 0L, 0L, 10_000L), stack.columns[0].cents)
        assertEquals(listOf(0L, 0L, 5_000L, 4_000L, 3_000L, 2_000L, 500L), stack.columns[1].cents)
        assertEquals(data.sumOf { it.amountCents }, stack.columns.sumOf { it.totalCents })
        assertEquals(listOf(7_000L, 6_000L, 5_000L, 4_000L, 3_000L, 2_000L, 10_500L), stack.totals)
    }

    @Test
    fun `a stack without a remainder has no Ostalo and ties are broken by name`() {
        val window = MonthWindow(ym(2026, 10), ym(2026, 10))
        val data = listOf(spend(100, d(2026, 10, 1), "Zabava"), spend(100, d(2026, 10, 1), "Hrana"))
        val stack = Analytics.trends(data, window).categoryStack
        assertEquals(listOf("Hrana", "Zabava"), stack.categories)
        assertEquals(listOf(100L, 100L), stack.columns.single().cents)
    }

    @Test
    fun `quarters cover the window including partial ones at the edges`() {
        assertEquals(
            listOf(Quarter(2024, 4), Quarter(2025, 1), Quarter(2025, 2)),
            Analytics.quarters(MonthWindow(ym(2024, 11), ym(2025, 4))),
        )
        assertEquals(Quarter(2025, 3), Quarter.of(ym(2025, 9)))
        assertEquals(ym(2025, 7), Quarter(2025, 3).firstMonth)
        assertEquals(Quarter(2026, 1), Quarter(2025, 4).next())
    }

    // ---- small multiples ----

    @Test
    fun `category series are the largest categories with total and monthly average`() {
        val window = MonthWindow(ym(2026, 7), ym(2026, 10))
        val data = listOf(
            spend(1_000, d(2026, 8, 1), "Hrana"),
            spend(2_001, d(2026, 9, 1), "Hrana"),
            spend(5_000, d(2026, 10, 1), "Režije"),
            spend(10, d(2026, 10, 1), "Zabava"),
        )
        val series = Analytics.trends(data, window).categorySeries
        assertEquals(listOf("Režije", "Hrana", "Zabava"), series.map { it.category })
        val hrana = series[1]
        assertEquals(3_001, hrana.totalCents)
        assertEquals(listOf(0L, 1_000L, 2_001L, 0L), hrana.months.map { it.cents })
        // Averaged over August..October (the history starts in August): 3001 / 3 = 1000.33 -> 1000.
        assertEquals(1_000, hrana.monthlyAverageCents)
        val limited = Analytics.categorySeries(data, window, ym(2026, 8), limit = 1)
        assertEquals(listOf("Režije"), limited.map { it.category })
    }

    // ---- cumulative by year ----

    @Test
    fun `cumulative years run January to December and the window's last year to its last month`() {
        val data = listOf(
            spend(100, d(2024, 1, 5)),
            spend(200, d(2024, 12, 5)),
            spend(50, d(2025, 3, 1)),
            spend(25, d(2026, 2, 1)),
        )
        val window = MonthWindow(ym(2025, 11), ym(2026, 3))
        val years = Analytics.trends(data, window).cumulativeByYear
        assertEquals(listOf(2025, 2026), years.map { it.year })
        // The whole of 2025, although the window starts in November.
        assertEquals(listOf(0L, 0L, 50L, 50L, 50L, 50L, 50L, 50L, 50L, 50L, 50L, 50L), years[0].cumulativeCents)
        assertEquals(listOf(0L, 25L, 25L), years[1].cumulativeCents)
        assertEquals(3, years[1].months)
        assertEquals(25, years[1].totalCents)

        val all = Analytics.trends(data, MonthWindow(ym(2024, 1), ym(2026, 3))).cumulativeByYear
        assertEquals(listOf(2024, 2025, 2026), all.map { it.year })
        assertEquals(300, all[0].totalCents)
        assertEquals(100, all[0].cumulativeCents[0])
    }

    @Test
    fun `cumulative years keep the newest six`() {
        val window = MonthWindow(ym(2018, 1), ym(2026, 1))
        val years = Analytics.cumulativeByYear(emptyList(), window)
        assertEquals((2021..2026).toList(), years.map { it.year })
    }

    // ---- payers ----

    @Test
    fun `payers split each month like the share rule and ignore the person filter`() {
        val window = MonthWindow(ym(2026, 9), ym(2026, 10))
        val data = listOf(
            spend(1_001, d(2026, 9, 1), paidBy = LOVEL), // odd HALF: Lovel's half 500 (half-even), Marina 501
            spend(2_000, d(2026, 9, 2), paidBy = MARINA, split = FULL_FOR_OTHER), // all Lovel's
            spend(400, d(2026, 10, 1), paidBy = MARINA),
            spend(5_000, d(2026, 10, 2), paidBy = LOVEL, category = Categories.TRANSFER),
        )
        val payers = Analytics.trends(data, window, MARINA).payers
        assertEquals(listOf(1_001L, 0L), payers.paid.getValue(LOVEL).map { it.cents })
        assertEquals(listOf(2_000L, 400L), payers.paid.getValue(MARINA).map { it.cents })
        assertEquals(listOf(2_500L, 200L), payers.share.getValue(LOVEL).map { it.cents })
        assertEquals(listOf(501L, 200L), payers.share.getValue(MARINA).map { it.cents })
        // Shares and payments add up to the month's spending.
        for (i in 0..1) {
            assertEquals(
                payers.paid.values.sumOf { it[i].cents },
                payers.share.values.sumOf { it[i].cents },
            )
        }
        val sept = Statistics.forMonth(data, ym(2026, 9))
        assertEquals(sept.shareByPerson[LOVEL], payers.share.getValue(LOVEL)[0].cents)
    }

    @Test
    fun `trends report is null without data`() {
        assertNull(Analytics.trends(emptyList(), AnalyticsRange.MONTHS_24, today))
    }
}
