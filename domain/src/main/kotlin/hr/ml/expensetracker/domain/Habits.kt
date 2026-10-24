package hr.ml.expensetracker.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** A day's spending. */
data class DayTotal(val date: LocalDate, val cents: Long)

/** The days from [from] to [to] (inclusive) without any spending, between two spending days. */
data class SpendingGap(val from: LocalDate, val to: LocalDate) {
    val days: Int get() = (ChronoUnit.DAYS.between(from, to) + 1).toInt()
}

/** The headline numbers of the Navike tab. */
data class HabitTiles(
    val expenseCount: Int,
    val totalCents: Long,
    /** [totalCents] over the window's months from the first month with spending on, half-even. */
    val monthlyAverageCents: Long,
    /** The middle expense amount (the mean of the two middle ones, half-even); null without any. */
    val medianCents: Long?,
    /** Expenses per week over [HabitsReport.days]. */
    val expensesPerWeek: Double,
    /** The mean gap between consecutive days with spending, in days; null with fewer than two such days. */
    val averageDaysBetween: Double?,
    /** The longest stretch without spending between two spending days; null when there is none. */
    val longestGap: SpendingGap?,
    /** The day with the most spending (the earliest of equal days); null without any. */
    val busiestDay: DayTotal?,
)

/** Expenses in one month: how many, how much and the average per expense (null without any). */
data class MonthActivity(val month: YearMonth, val count: Int, val cents: Long) {
    val averageCents: Long? get() = if (count == 0) null else divideHalfEven(cents, count.toLong())
}

/** Spending on one weekday over [HabitsReport.days]. */
data class WeekdayStat(
    val day: DayOfWeek,
    val count: Int,
    val cents: Long,
    /** How often this weekday occurs in the observed days. */
    val occurrences: Int,
) {
    /** Expenses on an average such day. */
    val averageCount: Double get() = if (occurrences == 0) 0.0 else count.toDouble() / occurrences
}

/** Spending on one day of the month (1..31) over [HabitsReport.days]. */
data class DayOfMonthStat(
    val day: Int,
    val cents: Long,
    /** How often this day of the month occurs in the observed days (31 only in long months). */
    val occurrences: Int,
) {
    /** Spending on an average such day, half-even; 0 when the day does not occur. */
    val averageCents: Long get() = if (occurrences == 0) 0L else divideHalfEven(cents, occurrences.toLong())
}

/**
 * Daily spending for the calendar heatmap: one value per day from [first] to [last] and the four
 * thresholds splitting the days with spending into [LEVELS] steps.
 */
data class SpendingCalendar(
    val first: LocalDate,
    val last: LocalDate,
    /** Spending per day, [first] first. */
    val days: List<Long>,
    /**
     * Upper bounds (inclusive, whole euros in cents) of levels 1..4: the 20th, 40th, 60th and 80th
     * percentile of the days with spending (nearest rank), rounded half-even to whole euros; level 5
     * is everything above the last. Non-decreasing.
     */
    val thresholds: List<Long>,
) {
    fun cents(date: LocalDate): Long = days[ChronoUnit.DAYS.between(first, date).toInt()]

    /** 0 for a day without spending, otherwise 1..[LEVELS]. */
    fun level(cents: Long): Int = when {
        cents <= 0L -> 0
        else -> 1 + thresholds.count { cents > it }
    }

    companion object {
        const val LEVELS = 5
    }
}

/** A histogram bucket: amounts from [fromCents] (inclusive) to [toCents] (exclusive; null: no limit). */
data class AmountBucket(val fromCents: Long, val toCents: Long?, val count: Int, val cents: Long) {
    operator fun contains(amount: Long): Boolean = amount >= fromCents && (toCents == null || amount < toCents)
}

/** A frequent expense description ([SearchText.fold]ed, so "SPAR" and "Spar" are one). */
data class DescriptionStat(
    /** The most used spelling (the latest of equally used ones). */
    val label: String,
    /** The most used category (the latest of equally used ones). */
    val category: String,
    val count: Int,
    val totalCents: Long,
) {
    val averageCents: Long get() = divideHalfEven(totalCents, count.toLong())
}

/** Everything the Navike tab draws, for one window and person selection. */
data class HabitsReport(
    val window: MonthWindow,
    val person: Person?,
    /**
     * The observed days: from the window's start (or the first spending day, when later) to the
     * window's end (or the later of today and the last spending day, when earlier).
     */
    val days: ClosedRange<LocalDate>,
    val tiles: HabitTiles,
    /** Every month of the window. */
    val activity: List<MonthActivity>,
    /** Monday to Sunday. */
    val weekdays: List<WeekdayStat>,
    /** Days 1 to 31. */
    val daysOfMonth: List<DayOfMonthStat>,
    /** The last [Habits.CALENDAR_MONTHS] months of [days]. */
    val calendar: SpendingCalendar,
    /** [Habits.BUCKET_BOUNDS] buckets, smallest first. */
    val histogram: List<AmountBucket>,
    val quarterStack: StackedSeries<Quarter>,
    /** At most [Habits.TOP_DESCRIPTIONS], most frequent first. */
    val topDescriptions: List<DescriptionStat>,
)

/**
 * Spending habits for the Navike tab: how often, on which days, how much per expense. Same
 * filters as [Analytics] (no deleted expenses, no transfers, only what [Person] paid when set).
 */
object Habits {
    /** The heatmap covers this many months, ending with the last observed day. */
    const val CALENDAR_MONTHS = 12

    const val TOP_DESCRIPTIONS = 10

    /** Lower bounds of the histogram buckets in cents: 0–10, 10–25, 25–50, 50–100, 100–250, 250–500, 500+ €. */
    val BUCKET_BOUNDS: List<Long> = listOf(0, 1_000, 2_500, 5_000, 10_000, 25_000, 50_000)

    fun habits(expenses: List<Expense>, range: AnalyticsRange, today: LocalDate, person: Person? = null): HabitsReport? {
        val window = Analytics.window(expenses, range, today) ?: return null
        return habits(expenses, window, today, person)
    }

    fun habits(expenses: List<Expense>, window: MonthWindow, today: LocalDate, person: Person? = null): HabitsReport {
        val spending = expenses.filter { Statistics.isSpending(it, person) }
        val inWindow = spending.filter { it.date in window }
        val days = observedDays(spending, window, today)
        val firstMonth = Trends.firstSpendingMonth(spending)
        return HabitsReport(
            window = window,
            person = person,
            days = days,
            tiles = tiles(inWindow, window, days, firstMonth),
            activity = activity(inWindow, window),
            weekdays = weekdays(inWindow, days),
            daysOfMonth = daysOfMonth(inWindow, days),
            calendar = calendar(inWindow, days),
            histogram = histogram(inWindow),
            quarterStack = Analytics.stack(inWindow, Analytics.quarters(window), { Quarter.of(YearMonth.from(it)) }),
            topDescriptions = topDescriptions(inWindow),
        )
    }

    /** See [HabitsReport.days]; [spending] is pre-filtered. */
    fun observedDays(spending: List<Expense>, window: MonthWindow, today: LocalDate): ClosedRange<LocalDate> {
        val firstDay = spending.minOfOrNull { it.date }
        val lastDay = spending.maxOfOrNull { it.date }
        val start = if (firstDay != null && firstDay.isAfter(window.start)) minOf(firstDay, window.end) else window.start
        val latest = if (lastDay != null && lastDay.isAfter(today)) lastDay else today
        val end = maxOf(start, minOf(window.end, latest))
        return start..end
    }

    fun tiles(inWindow: List<Expense>, window: MonthWindow, days: ClosedRange<LocalDate>, firstMonth: YearMonth?): HabitTiles {
        val total = inWindow.sumOf { it.amountCents }
        val averagedMonths = if (firstMonth == null) 0 else window.months.count { it >= firstMonth }
        val dayTotals = inWindow.groupingBy { it.date }.fold(0L) { acc, e -> acc + e.amountCents }.toSortedMap()
        val spendingDays = dayTotals.keys.toList()
        var gap: SpendingGap? = null
        for (i in 1 until spendingDays.size) {
            val between = ChronoUnit.DAYS.between(spendingDays[i - 1], spendingDays[i]) - 1
            if (between > 0 && (gap == null || between > gap.days)) {
                gap = SpendingGap(spendingDays[i - 1].plusDays(1), spendingDays[i].minusDays(1))
            }
        }
        val busiest = dayTotals.entries.fold(null as DayTotal?) { best, (date, cents) ->
            if (best == null || cents > best.cents) DayTotal(date, cents) else best
        }
        return HabitTiles(
            expenseCount = inWindow.size,
            totalCents = total,
            monthlyAverageCents = if (averagedMonths == 0) 0L else divideHalfEven(total, averagedMonths.toLong()),
            medianCents = median(inWindow.map { it.amountCents }),
            expensesPerWeek = inWindow.size * 7.0 / dayCount(days),
            averageDaysBetween = if (spendingDays.size < 2) {
                null
            } else {
                ChronoUnit.DAYS.between(spendingDays.first(), spendingDays.last()).toDouble() / (spendingDays.size - 1)
            },
            longestGap = gap,
            busiestDay = busiest,
        )
    }

    /** The median of [amounts]: the middle value, or the half-even mean of the two middle ones. */
    fun median(amounts: List<Long>): Long? {
        if (amounts.isEmpty()) return null
        val sorted = amounts.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else divideHalfEven(sorted[mid - 1] + sorted[mid], 2)
    }

    fun activity(inWindow: List<Expense>, window: MonthWindow): List<MonthActivity> {
        val counts = IntArray(window.size)
        val cents = LongArray(window.size)
        for (e in inWindow) {
            val i = window.indexOf(YearMonth.from(e.date))
            counts[i]++
            cents[i] += e.amountCents
        }
        return window.months.mapIndexed { i, m -> MonthActivity(m, counts[i], cents[i]) }
    }

    fun weekdays(inWindow: List<Expense>, days: ClosedRange<LocalDate>): List<WeekdayStat> {
        val occurrences = IntArray(7)
        forEachDay(days) { occurrences[it.dayOfWeek.ordinal]++ }
        val counts = IntArray(7)
        val cents = LongArray(7)
        for (e in inWindow) {
            if (e.date !in days) continue
            counts[e.date.dayOfWeek.ordinal]++
            cents[e.date.dayOfWeek.ordinal] += e.amountCents
        }
        return DayOfWeek.entries.map { WeekdayStat(it, counts[it.ordinal], cents[it.ordinal], occurrences[it.ordinal]) }
    }

    fun daysOfMonth(inWindow: List<Expense>, days: ClosedRange<LocalDate>): List<DayOfMonthStat> {
        val occurrences = IntArray(31)
        forEachDay(days) { occurrences[it.dayOfMonth - 1]++ }
        val cents = LongArray(31)
        for (e in inWindow) if (e.date in days) cents[e.date.dayOfMonth - 1] += e.amountCents
        return (1..31).map { DayOfMonthStat(it, cents[it - 1], occurrences[it - 1]) }
    }

    /** The last [CALENDAR_MONTHS] months of [days] (from the day after the same date a year earlier). */
    fun calendar(inWindow: List<Expense>, days: ClosedRange<LocalDate>): SpendingCalendar {
        val last = days.endInclusive
        val first = maxOf(days.start, last.minusMonths(CALENDAR_MONTHS.toLong()).plusDays(1))
        val n = (ChronoUnit.DAYS.between(first, last) + 1).toInt()
        val totals = LongArray(n)
        for (e in inWindow) {
            if (e.date.isBefore(first) || e.date.isAfter(last)) continue
            totals[ChronoUnit.DAYS.between(first, e.date).toInt()] += e.amountCents
        }
        val positive = totals.filter { it > 0 }.sorted()
        val thresholds = if (positive.isEmpty()) {
            emptyList()
        } else {
            listOf(20, 40, 60, 80).map { p ->
                // Nearest rank: the smallest value with at least p % of the days at or below it.
                val rank = maxOf(1, (p * positive.size + 99) / 100)
                divideHalfEven(positive[rank - 1], 100) * 100
            }
        }
        return SpendingCalendar(first, last, totals.toList(), thresholds)
    }

    fun histogram(inWindow: List<Expense>): List<AmountBucket> {
        val counts = IntArray(BUCKET_BOUNDS.size)
        val cents = LongArray(BUCKET_BOUNDS.size)
        for (e in inWindow) {
            val i = BUCKET_BOUNDS.indexOfLast { e.amountCents >= it }
            counts[i]++
            cents[i] += e.amountCents
        }
        return BUCKET_BOUNDS.mapIndexed { i, from -> AmountBucket(from, BUCKET_BOUNDS.getOrNull(i + 1), counts[i], cents[i]) }
    }

    /**
     * The most frequent descriptions (blank ones skipped), grouped by their [SearchText.fold] with
     * inner whitespace collapsed; ordered by count, then total, then label.
     */
    fun topDescriptions(inWindow: List<Expense>, limit: Int = TOP_DESCRIPTIONS): List<DescriptionStat> {
        val groups = inWindow.filter { it.description.isNotBlank() }
            .groupBy { SearchText.fold(it.description).trim().replace(WHITESPACE, " ") }
        return groups.values.map { group ->
            DescriptionStat(
                label = mostUsed(group) { it.description.trim().replace(WHITESPACE, " ") },
                category = mostUsed(group) { it.category },
                count = group.size,
                totalCents = group.sumOf { it.amountCents },
            )
        }
            .sortedWith(compareByDescending<DescriptionStat> { it.count }.thenByDescending { it.totalCents }.thenBy { it.label })
            .take(limit)
    }

    /** The most frequent value of [key] in [group]; ties go to the value used most recently. */
    private fun mostUsed(group: List<Expense>, key: (Expense) -> String): String {
        val stats = HashMap<String, Pair<Int, LocalDate>>()
        for (e in group) {
            val k = key(e)
            val (count, latest) = stats[k] ?: (0 to e.date)
            stats[k] = (count + 1) to maxOf(latest, e.date)
        }
        return stats.entries.maxWith(compareBy<Map.Entry<String, Pair<Int, LocalDate>>> { it.value.first }.thenBy { it.value.second }.thenBy { it.key }).key
    }

    private inline fun forEachDay(days: ClosedRange<LocalDate>, action: (LocalDate) -> Unit) {
        var d = days.start
        while (!d.isAfter(days.endInclusive)) {
            action(d)
            d = d.plusDays(1)
        }
    }

    private fun dayCount(days: ClosedRange<LocalDate>): Long = ChronoUnit.DAYS.between(days.start, days.endInclusive) + 1

    private val WHITESPACE = Regex("\\s+")
}
