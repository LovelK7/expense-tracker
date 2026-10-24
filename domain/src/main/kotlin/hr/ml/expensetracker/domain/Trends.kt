package hr.ml.expensetracker.domain

import java.time.LocalDate
import java.time.YearMonth

/** Spending in one calendar month, in cents. */
data class MonthTotal(val month: YearMonth, val cents: Long)

/**
 * Spending over [current] next to the same figure over [reference] (both inclusive). Like
 * [Statistics], it leaves out deleted expenses and transfers and, with a person, counts only
 * what that person paid.
 */
data class PeriodComparison(
    val current: ClosedRange<LocalDate>,
    val reference: ClosedRange<LocalDate>,
    val currentCents: Long,
    val referenceCents: Long,
    /** Spending expenses in [reference]. */
    val referenceCount: Int,
    /**
     * The change from [referenceCents] to [currentCents] in whole percent, half-even
     * ([Percent.change]). Null when the reference period has no spending to compare with.
     */
    val changePercent: Int?,
    /** Both ranges stop at the same day of the year as today (the current year, "do danas"). */
    val yearToDate: Boolean = false,
)

/** One category's spending month by month, for the expanded category row. */
data class CategoryTrend(
    val category: String,
    /** Chronological, every month of the window, zero for months without spending. */
    val months: List<MonthTotal>,
    /** [averagedMonths] months of the window from the first month with any spending on. */
    val averagedMonths: Int,
    /** The window's total over [averagedMonths], half-even; 0 when there is nothing to average. */
    val monthlyAverageCents: Long,
)

/** Spending in one calendar year, newest first in [Trends.byYear]. */
data class YearTotal(
    val year: Int,
    val cents: Long,
    val expenseCount: Int,
    /**
     * The months the year's average is taken over: from the first month with any spending (all
     * time) to the current month (or the latest spending month when that is later), clipped to
     * the year. A past year in the middle of the history has 12.
     */
    val months: Int,
    /** [cents] / [months], half-even. */
    val monthlyAverageCents: Long,
)

/**
 * Trends over the spending history: period-over-period comparisons, a category's monthly series
 * and the totals per year. Pure functions over the expense list; the rules are in
 * docs/DESIGN.md, "Statistics rules".
 */
object Trends {
    /** The default length of [categoryTrend]'s window. */
    const val TREND_MONTHS = 12

    fun compare(
        expenses: Iterable<Expense>,
        current: ClosedRange<LocalDate>,
        reference: ClosedRange<LocalDate>,
        person: Person? = null,
        yearToDate: Boolean = false,
    ): PeriodComparison {
        var currentCents = 0L
        var referenceCents = 0L
        var referenceCount = 0
        for (e in expenses) {
            if (!Statistics.isSpending(e, person)) continue
            if (e.date in current) currentCents += e.amountCents
            if (e.date in reference) {
                referenceCents += e.amountCents
                referenceCount++
            }
        }
        return PeriodComparison(
            current = current,
            reference = reference,
            currentCents = currentCents,
            referenceCents = referenceCents,
            referenceCount = referenceCount,
            changePercent = if (referenceCount == 0) null else Percent.change(currentCents, referenceCents),
            yearToDate = yearToDate,
        )
    }

    /** [month] against the month before it. */
    fun previousMonth(expenses: Iterable<Expense>, month: YearMonth, person: Person? = null): PeriodComparison =
        compare(expenses, month.range(), month.minusMonths(1).range(), person)

    /** [month] against the same month a year earlier. */
    fun sameMonthLastYear(expenses: Iterable<Expense>, month: YearMonth, person: Person? = null): PeriodComparison =
        compare(expenses, month.range(), month.minusYears(1).range(), person)

    /**
     * [year] against the year before. For the current year (that of [today]) both ranges run from
     * 1 January to today's date ([today] a year earlier, 29 February becoming the 28th), so a
     * partial year is not compared with a full one ([PeriodComparison.yearToDate]).
     */
    fun previousYear(expenses: Iterable<Expense>, year: Int, today: LocalDate, person: Person? = null): PeriodComparison {
        val jan1 = LocalDate.of(year, 1, 1)
        return if (year == today.year) {
            compare(expenses, jan1..today, jan1.minusYears(1)..today.minusYears(1), person, yearToDate = true)
        } else {
            compare(expenses, jan1..LocalDate.of(year, 12, 31), jan1.minusYears(1)..LocalDate.of(year - 1, 12, 31), person)
        }
    }

    /**
     * Spending per month for the [months] months ending with [end] (inclusive), chronological,
     * zero for months without any. Only [category] when set; only what [person] paid when set.
     */
    fun monthlySeries(
        expenses: Iterable<Expense>,
        end: YearMonth,
        months: Int = TREND_MONTHS,
        category: String? = null,
        person: Person? = null,
    ): List<MonthTotal> {
        require(months > 0) { "months must be positive: $months" }
        val start = end.minusMonths(months - 1L)
        val sums = LongArray(months)
        for (e in expenses) {
            if (!Statistics.isSpending(e, person) || (category != null && e.category != category)) continue
            val m = YearMonth.from(e.date)
            if (m < start || m > end) continue
            sums[monthsBetween(start, m)] += e.amountCents
        }
        return List(months) { MonthTotal(start.plusMonths(it.toLong()), sums[it]) }
    }

    /**
     * [category]'s [months]-month series ending with [end] plus its monthly average. The average
     * divides the window's total by the window's months from the first month with any spending
     * (of [person], all categories) on, so the start of the history does not drag it down.
     */
    fun categoryTrend(
        expenses: Iterable<Expense>,
        category: String,
        end: YearMonth,
        person: Person? = null,
        months: Int = TREND_MONTHS,
    ): CategoryTrend {
        val series = monthlySeries(expenses, end, months, category, person)
        val first = firstSpendingMonth(expenses, person)
        val averaged = if (first == null) 0 else series.count { it.month >= first }
        val total = series.filter { first != null && it.month >= first }.sumOf { it.cents }
        return CategoryTrend(
            category = category,
            months = series,
            averagedMonths = averaged,
            monthlyAverageCents = if (averaged == 0) 0L else divideHalfEven(total, averaged.toLong()),
        )
    }

    /**
     * Spending per calendar year, newest first, every year from the first to the last one with
     * spending (zero rows for empty years in between), with the monthly average of
     * [YearTotal.months]. Empty without spending.
     */
    fun byYear(expenses: Iterable<Expense>, today: LocalDate, person: Person? = null): List<YearTotal> {
        val cents = HashMap<Int, Long>()
        val counts = HashMap<Int, Int>()
        var first: YearMonth? = null
        var last: YearMonth? = null
        for (e in expenses) {
            if (!Statistics.isSpending(e, person)) continue
            val m = YearMonth.from(e.date)
            if (first == null || m < first) first = m
            if (last == null || m > last) last = m
            cents.merge(m.year, e.amountCents, Long::plus)
            counts.merge(m.year, 1, Int::plus)
        }
        if (first == null || last == null) return emptyList()
        val averageEnd = maxOf(YearMonth.from(today), last)
        return (last.year downTo first.year).map { year ->
            val fromMonth = if (year == first.year) first.monthValue else 1
            val toMonth = if (year == averageEnd.year) averageEnd.monthValue else 12
            val months = toMonth - fromMonth + 1
            val total = cents[year] ?: 0L
            YearTotal(year, total, counts[year] ?: 0, months, divideHalfEven(total, months.toLong()))
        }
    }

    /** The earliest month with spending (of [person], when set); null without any. */
    fun firstSpendingMonth(expenses: Iterable<Expense>, person: Person? = null): YearMonth? {
        var first: LocalDate? = null
        for (e in expenses) {
            if (Statistics.isSpending(e, person) && (first == null || e.date.isBefore(first))) first = e.date
        }
        return first?.let(YearMonth::from)
    }

    private fun YearMonth.range(): ClosedRange<LocalDate> = atDay(1)..atEndOfMonth()

    private fun monthsBetween(from: YearMonth, to: YearMonth): Int =
        (to.year - from.year) * 12 + (to.monthValue - from.monthValue)
}

/** Whole-number percentages, rounded half-to-even, and their display. */
object Percent {
    /** The real minus sign, not a hyphen. */
    const val MINUS = '−'

    /** [part] as a whole percentage of [whole], half-even; null when [whole] is not positive. */
    fun of(part: Long, whole: Long): Int? = if (whole <= 0L) null else divideHalfEven(part * 100, whole).toInt()

    /** The change from [reference] to [current] in whole percent, half-even; null when [reference] is not positive. */
    fun change(current: Long, reference: Long): Int? =
        if (reference <= 0L) null else divideHalfEven((current - reference) * 100, reference).toInt()

    /** "12 %", "−5 %" (non-breaking space before the sign). */
    fun format(percent: Int): String = "${signed(percent, plus = false)}${Money.NBSP}%"

    /** A change: "+12 %", "−5 %", "0 %". */
    fun formatChange(percent: Int): String = "${signed(percent, plus = true)}${Money.NBSP}%"

    private fun signed(percent: Int, plus: Boolean): String = when {
        percent < 0 -> "$MINUS${-percent.toLong()}"
        percent > 0 && plus -> "+$percent"
        else -> "$percent"
    }
}

/** [a] / [b] rounded half-to-even; [b] must be positive. */
internal fun divideHalfEven(a: Long, b: Long): Long {
    require(b > 0) { "divisor must be positive: $b" }
    val q = Math.floorDiv(a, b)
    val twiceRemainder = 2 * Math.floorMod(a, b)
    return when {
        twiceRemainder < b -> q
        twiceRemainder > b -> q + 1
        else -> if (q % 2L == 0L) q else q + 1
    }
}
