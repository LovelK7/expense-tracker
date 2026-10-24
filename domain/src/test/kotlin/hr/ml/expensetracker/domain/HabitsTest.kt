package hr.ml.expensetracker.domain

import hr.ml.expensetracker.domain.Person.LOVEL
import hr.ml.expensetracker.domain.Person.MARINA
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HabitsTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)
    private fun ym(y: Int, m: Int) = YearMonth.of(y, m)
    private val today = d(2026, 10, 24)

    private fun spend(
        cents: Long,
        date: LocalDate,
        category: String = "Hrana",
        description: String = "",
        paidBy: Person = LOVEL,
    ) = expense(cents, paidBy, SplitType.HALF, date, category).copy(description = description)

    // ---- observed days ----

    @Test
    fun `observed days start at the first spending day and stop today`() {
        val window = MonthWindow(ym(2026, 9), ym(2026, 10))
        val data = listOf(spend(100, d(2026, 9, 10)))
        assertEquals(d(2026, 9, 10)..today, Habits.observedDays(data, window, today))
        // Spending before the window: the window's start; a future expense moves the end.
        val more = data + spend(100, d(2026, 1, 1)) + spend(100, d(2026, 10, 30))
        assertEquals(d(2026, 9, 1)..d(2026, 10, 30), Habits.observedDays(more, window, today))
        // Without spending: the window up to today.
        assertEquals(d(2026, 9, 1)..today, Habits.observedDays(emptyList(), window, today))
    }

    // ---- tiles ----

    @Test
    fun `tiles report median, frequency, gaps and the busiest day`() {
        val window = MonthWindow(ym(2026, 10), ym(2026, 10))
        val data = listOf(
            spend(1_000, d(2026, 10, 1)),
            spend(3_000, d(2026, 10, 1)),
            spend(500, d(2026, 10, 2)),
            spend(2_001, d(2026, 10, 8)), // 5 days without spending before it (3rd..7th)
            spend(4_000, d(2026, 10, 10)),
            spend(9_999, d(2026, 10, 11), category = Categories.TRANSFER),
        )
        val tiles = Habits.habits(data, window, today).tiles
        assertEquals(5, tiles.expenseCount)
        assertEquals(10_501, tiles.totalCents)
        assertEquals(10_501, tiles.monthlyAverageCents)
        assertEquals(2_001, tiles.medianCents)
        // 5 expenses over 1..24 October (24 days): 5 * 7 / 24.
        assertEquals(5 * 7.0 / 24, tiles.expensesPerWeek, 1e-9)
        // Spending days 1, 2, 8, 10: (10 - 1) / 3 = 3 days apart on average.
        assertEquals(3.0, tiles.averageDaysBetween!!, 1e-9)
        assertEquals(SpendingGap(d(2026, 10, 3), d(2026, 10, 7)), tiles.longestGap)
        assertEquals(5, tiles.longestGap!!.days)
        assertEquals(DayTotal(d(2026, 10, 1), 4_000), tiles.busiestDay)
    }

    @Test
    fun `median of an even count is the half-even mean of the middle pair`() {
        assertEquals(150, Habits.median(listOf(100, 200)))
        assertEquals(2, Habits.median(listOf(1, 4))) // 2.5 -> 2
        assertEquals(4, Habits.median(listOf(3, 4, 1, 9))) // 3.5 -> 4
        assertEquals(7, Habits.median(listOf(7)))
        assertNull(Habits.median(emptyList()))
    }

    @Test
    fun `the busiest day prefers the earliest of equal days and tiles cope with one day`() {
        val window = MonthWindow(ym(2026, 10), ym(2026, 10))
        val tiles = Habits.habits(listOf(spend(500, d(2026, 10, 5)), spend(500, d(2026, 10, 9))), window, today).tiles
        assertEquals(DayTotal(d(2026, 10, 5), 500), tiles.busiestDay)
        val single = Habits.habits(listOf(spend(500, d(2026, 10, 5))), window, today).tiles
        assertNull(single.averageDaysBetween)
        assertNull(single.longestGap)
    }

    // ---- activity, weekdays, days of month ----

    @Test
    fun `activity counts expenses per month with the average per expense`() {
        val window = MonthWindow(ym(2026, 8), ym(2026, 10))
        val data = listOf(spend(100, d(2026, 8, 1)), spend(201, d(2026, 8, 2)), spend(1_000, d(2026, 10, 3)))
        val activity = Habits.habits(data, window, today).activity
        assertEquals(listOf(2, 0, 1), activity.map { it.count })
        assertEquals(listOf(301L, 0L, 1_000L), activity.map { it.cents })
        assertEquals(listOf(150L, null, 1_000L), activity.map { it.averageCents }) // 150.5 -> 150
    }

    @Test
    fun `weekday stats count occurrences of each weekday in the observed days`() {
        // Monday 5 October to Sunday 18 October 2026: every weekday twice.
        val days = d(2026, 10, 5)..d(2026, 10, 18)
        val data = listOf(spend(300, d(2026, 10, 5)), spend(100, d(2026, 10, 12)), spend(600, d(2026, 10, 17)))
        val stats = Habits.weekdays(data, days)
        assertEquals(DayOfWeek.entries.toList(), stats.map { it.day })
        assertTrue(stats.all { it.occurrences == 2 })
        assertEquals(2, stats[0].count)
        assertEquals(400, stats[0].cents)
        assertEquals(1.0, stats[0].averageCount, 1e-9)
        assertEquals(600, stats[5].cents) // Saturday
        assertEquals(0.5, stats[5].averageCount, 1e-9)
        assertEquals(0, stats[6].count)
    }

    @Test
    fun `day-of-month averages divide by how often the day occurs`() {
        // 1 January to 31 March 2026: day 31 occurs twice (January, March), day 30 twice, day 1 three times.
        val days = d(2026, 1, 1)..d(2026, 3, 31)
        val data = listOf(spend(60_000, d(2026, 1, 1)), spend(60_001, d(2026, 2, 1)), spend(900, d(2026, 3, 31)))
        val stats = Habits.daysOfMonth(data, days)
        assertEquals((1..31).toList(), stats.map { it.day })
        assertEquals(3, stats[0].occurrences)
        assertEquals(40_000, stats[0].averageCents) // 120001 / 3 = 40000.33
        assertEquals(2, stats[29].occurrences)
        assertEquals(2, stats[30].occurrences)
        assertEquals(450, stats[30].averageCents)
        assertEquals(0, stats[14].averageCents)
    }

    // ---- calendar ----

    @Test
    fun `the calendar covers the last twelve months with quantile thresholds in whole euros`() {
        val days = d(2025, 1, 1)..today
        val data = (1..10).map { spend(it * 1_000L + 40, d(2026, 10, it)) } + spend(5_000, d(2025, 6, 1))
        val calendar = Habits.calendar(data, days)
        assertEquals(d(2025, 10, 25), calendar.first)
        assertEquals(today, calendar.last)
        assertEquals(365, calendar.days.size)
        assertEquals(3_040, calendar.cents(d(2026, 10, 3)))
        assertEquals(0, calendar.cents(d(2026, 10, 20)))
        // Ten days 10.40 .. 100.40 €: nearest-rank 20/40/60/80 % = 2nd, 4th, 6th, 8th value, in whole euros.
        assertEquals(listOf(2_000L, 4_000L, 6_000L, 8_000L), calendar.thresholds)
        assertEquals(0, calendar.level(0))
        assertEquals(1, calendar.level(1_040))
        assertEquals(1, calendar.level(2_000))
        assertEquals(2, calendar.level(2_001))
        assertEquals(5, calendar.level(10_040))
        assertEquals(data.filter { it.date >= calendar.first }.sumOf { it.amountCents }, calendar.days.sum())
    }

    @Test
    fun `a short history gives a short calendar and no thresholds without spending`() {
        val calendar = Habits.calendar(emptyList(), d(2026, 10, 1)..today)
        assertEquals(d(2026, 10, 1), calendar.first)
        assertEquals(24, calendar.days.size)
        assertEquals(emptyList(), calendar.thresholds)
        assertEquals(0, calendar.level(0))
    }

    // ---- histogram ----

    @Test
    fun `histogram buckets are half-open and the last one has no limit`() {
        val data = listOf(0L, 999L, 1_000L, 2_499L, 2_500L, 9_999L, 10_000L, 49_999L, 50_000L, 1_000_000L)
            .map { spend(it, d(2026, 10, 1)) }
        val buckets = Habits.histogram(data)
        assertEquals(Habits.BUCKET_BOUNDS, buckets.map { it.fromCents })
        assertEquals(listOf(2, 2, 1, 1, 1, 1, 2), buckets.map { it.count })
        assertNull(buckets.last().toCents)
        assertEquals(1_000L, buckets.first().toCents)
        assertTrue(999L in buckets[0] && 1_000L !in buckets[0])
        assertEquals(data.size, buckets.sumOf { it.count })
        assertEquals(data.sumOf { it.amountCents }, buckets.sumOf { it.cents })
    }

    // ---- quarters ----

    @Test
    fun `quarter stack uses the window's quarters`() {
        val window = MonthWindow(ym(2026, 5), ym(2026, 10))
        val data = listOf(spend(100, d(2026, 5, 1), "Hrana"), spend(300, d(2026, 8, 1), "Režije"), spend(50, d(2026, 10, 2), "Hrana"))
        val stack = Habits.habits(data, window, today).quarterStack
        assertEquals(listOf(Quarter(2026, 2), Quarter(2026, 3), Quarter(2026, 4)), stack.columns.map { it.period })
        assertEquals(listOf("Režije", "Hrana"), stack.categories)
        assertEquals(listOf(listOf(0L, 100L), listOf(300L, 0L), listOf(0L, 50L)), stack.columns.map { it.cents })
    }

    // ---- top descriptions ----

    @Test
    fun `top descriptions fold case and diacritics and keep the most used spelling`() {
        val data = listOf(
            spend(1_000, d(2026, 10, 1), "Hrana", "Konzum"),
            spend(2_000, d(2026, 10, 2), "Hrana", "KONZUM "),
            spend(3_001, d(2026, 10, 3), "Potrepštine", "konzum"),
            spend(500, d(2026, 10, 4), "Hrana", "Konzum"),
            spend(800, d(2026, 10, 5), "Režije", "Struja  HEP"),
            spend(900, d(2026, 10, 6), "Režije", "struja hep"),
            spend(9_000, d(2026, 10, 7), "Hrana van", "Ćevapi"),
            spend(9_000, d(2026, 10, 8), "Hrana van", "cevapi"),
            spend(100, d(2026, 10, 9), "Hrana", "   "),
        )
        val top = Habits.topDescriptions(data)
        assertEquals(listOf("Konzum", "cevapi", "struja hep"), top.map { it.label })
        assertEquals(listOf(4, 2, 2), top.map { it.count })
        assertEquals("Hrana", top[0].category)
        assertEquals(6_501, top[0].totalCents)
        assertEquals(1_625, top[0].averageCents) // 1625.25
        // Equal counts: the larger total first; equal spellings: the latest one ("cevapi").
        assertEquals("Hrana van", top[1].category)
        assertEquals(1, Habits.topDescriptions(data, limit = 1).size)
    }

    @Test
    fun `habits follow the person filter`() {
        val window = MonthWindow(ym(2026, 10), ym(2026, 10))
        val data = listOf(spend(100, d(2026, 10, 1), paidBy = MARINA), spend(200, d(2026, 10, 2), paidBy = LOVEL))
        val marina = Habits.habits(data, window, today, MARINA)
        assertEquals(1, marina.tiles.expenseCount)
        assertEquals(100, marina.histogram.sumOf { it.cents })
        assertNull(Habits.habits(emptyList(), AnalyticsRange.ALL, today))
    }
}
