package hr.ml.expensetracker.domain

import java.time.LocalDate
import java.time.YearMonth

/** The span the Trendovi and Navike tabs look at: the last 12 or 24 months, or everything. */
enum class AnalyticsRange(val months: Int?) {
    MONTHS_12(12),
    MONTHS_24(24),
    ALL(null),
}

/** The calendar months [first]..[last], both inclusive. */
data class MonthWindow(val first: YearMonth, val last: YearMonth) {
    init {
        require(!last.isBefore(first)) { "Empty window: $first..$last" }
    }

    val start: LocalDate get() = first.atDay(1)
    val end: LocalDate get() = last.atEndOfMonth()

    /** Number of months in the window. */
    val size: Int get() = monthsBetween(first, last) + 1

    /** Every month of the window, chronological. */
    val months: List<YearMonth> get() = List(size) { first.plusMonths(it.toLong()) }

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)

    operator fun contains(month: YearMonth): Boolean = month >= first && month <= last

    /** The month's position in [months]; only for months in the window. */
    fun indexOf(month: YearMonth): Int = monthsBetween(first, month)
}

/** A calendar quarter, e.g. Q3 2025. */
data class Quarter(val year: Int, val quarter: Int) : Comparable<Quarter> {
    init {
        require(quarter in 1..4) { "quarter must be 1..4: $quarter" }
    }

    val firstMonth: YearMonth get() = YearMonth.of(year, quarter * 3 - 2)

    fun next(): Quarter = if (quarter == 4) Quarter(year + 1, 1) else Quarter(year, quarter + 1)

    override fun compareTo(other: Quarter): Int = compareValuesBy(this, other, Quarter::year, Quarter::quarter)

    companion object {
        fun of(month: YearMonth): Quarter = Quarter(month.year, (month.monthValue - 1) / 3 + 1)
    }
}

/** One column of a stacked chart: [cents] per category, aligned with [StackedSeries.categories]. */
data class StackColumn<P>(val period: P, val cents: List<Long>) {
    val totalCents: Long get() = cents.sum()
}

/**
 * Spending per period split by category: the top categories of the window by total (largest
 * first) and, when anything is left, [Analytics.OTHER] last for the rest (see [Analytics.stack]).
 */
data class StackedSeries<P>(val categories: List<String>, val columns: List<StackColumn<P>>) {
    /** Each category's total over all columns, aligned with [categories]. */
    val totals: List<Long> get() = categories.indices.map { i -> columns.sumOf { it.cents[i] } }
}

/** One category's spending month by month over the window (the small multiples). */
data class CategorySeries(
    val category: String,
    /** Every month of the window, zero for months without spending. */
    val months: List<MonthTotal>,
    val totalCents: Long,
    /** [totalCents] over the window's months from the first month with any spending on, half-even. */
    val monthlyAverageCents: Long,
)

/**
 * A calendar year's spending added up month by month: [cumulativeCents] has one value per month
 * from January to [months] (12, or the window's last month in its year), each the year's total up
 * to the end of that month.
 */
data class YearCumulative(val year: Int, val cumulativeCents: List<Long>) {
    val months: Int get() = cumulativeCents.size
    val totalCents: Long get() = cumulativeCents.lastOrNull() ?: 0L
}

/**
 * Who paid how much, month by month, over the window, for both people and whoever is selected:
 * [paid] is what each person paid, [share] what each person consumed (half of the shared expenses
 * plus everything paid on their behalf, the [Statistics.shareByPerson] rule per month). Both lists
 * cover every month of the window.
 */
data class PayerSeries(
    val paid: Map<Person, List<MonthTotal>>,
    val share: Map<Person, List<MonthTotal>>,
)

/** Everything the Trendovi tab draws, for one window and person selection. */
data class TrendsReport(
    val window: MonthWindow,
    val person: Person?,
    /** Spending per month, every month of the window. */
    val monthly: List<MonthTotal>,
    /**
     * The [Analytics.ROLLING_MONTHS]-month trailing average of [monthly], half-even: the month and
     * up to two before it, counting only months from the first month with spending on (earlier
     * months of the history are used when they are before the window). Starts with the first
     * month of the window that has such a month, so it can be shorter than [monthly].
     */
    val rollingAverage: List<MonthTotal>,
    /** Top [Analytics.STACK_CATEGORIES] categories + "Ostalo" per month. */
    val categoryStack: StackedSeries<YearMonth>,
    /** The top [Analytics.SMALL_MULTIPLES] categories, largest first. */
    val categorySeries: List<CategorySeries>,
    /** Each calendar year the window touches (at most [Analytics.MAX_YEARS], oldest first), in full. */
    val cumulativeByYear: List<YearCumulative>,
    /** Both people's payments and shares; not filtered by [person]. */
    val payers: PayerSeries,
) {
    val totalCents: Long get() = monthly.sumOf { it.cents }
}

/**
 * Time series over the spending history for the Trendovi tab. Like [Statistics] they leave out
 * deleted expenses and transfers ("Prijenos") and, with a person, count only what that person
 * paid. Rules: docs/DESIGN.md, "Analytics rules".
 */
object Analytics {
    /** The folded remainder of a stacked chart; the real category "Ostalo" always goes into it. */
    const val OTHER = "Ostalo"

    /** Named categories in a stacked chart (plus [OTHER]). */
    const val STACK_CATEGORIES = 6

    /** Categories drawn as small multiples. */
    const val SMALL_MULTIPLES = 8

    /** Months in the rolling average. */
    const val ROLLING_MONTHS = 3

    /** Years in the cumulative chart at most (the newest ones). */
    const val MAX_YEARS = 6

    /**
     * The months [range] covers: it ends with the later of today's month and the month of the
     * latest non-deleted expense and starts [AnalyticsRange.months] − 1 months earlier, but not
     * before the month of the earliest non-deleted expense ([AnalyticsRange.ALL] starts there).
     * Transfers count for the range, so it is the same whoever is selected. Null without any
     * non-deleted expense.
     */
    fun window(expenses: Iterable<Expense>, range: AnalyticsRange, today: LocalDate): MonthWindow? {
        val live = Statistics.liveRange(expenses) ?: return null
        val earliest = YearMonth.from(live.start)
        val last = maxOf(YearMonth.from(today), YearMonth.from(live.endInclusive))
        val first = when (val months = range.months) {
            null -> earliest
            else -> maxOf(earliest, last.minusMonths(months - 1L))
        }
        return MonthWindow(minOf(first, last), last)
    }

    /** The Trendovi report for [range] and [person]; null when there is no data at all. */
    fun trends(expenses: List<Expense>, range: AnalyticsRange, today: LocalDate, person: Person? = null): TrendsReport? {
        val window = window(expenses, range, today) ?: return null
        return trends(expenses, window, person)
    }

    fun trends(expenses: List<Expense>, window: MonthWindow, person: Person? = null): TrendsReport {
        val spending = expenses.filter { Statistics.isSpending(it, person) }
        val inWindow = spending.filter { it.date in window }
        val firstMonth = Trends.firstSpendingMonth(spending)
        return TrendsReport(
            window = window,
            person = person,
            monthly = monthlySeries(inWindow, window),
            rollingAverage = rollingAverage(spending, window, firstMonth),
            categoryStack = stack(inWindow, window.months, YearMonth::from),
            categorySeries = categorySeries(inWindow, window, firstMonth),
            cumulativeByYear = cumulativeByYear(spending, window),
            payers = payers(expenses, window),
        )
    }

    /** Spending per month of [window], zero for months without any; [spending] is pre-filtered. */
    fun monthlySeries(spending: Iterable<Expense>, window: MonthWindow, category: String? = null): List<MonthTotal> {
        val sums = LongArray(window.size)
        for (e in spending) {
            if (e.date !in window || (category != null && e.category != category)) continue
            sums[window.indexOf(YearMonth.from(e.date))] += e.amountCents
        }
        return window.months.mapIndexed { i, m -> MonthTotal(m, sums[i]) }
    }

    /** See [TrendsReport.rollingAverage]; [spending] is pre-filtered, [firstMonth] its first month. */
    fun rollingAverage(spending: Iterable<Expense>, window: MonthWindow, firstMonth: YearMonth?): List<MonthTotal> {
        if (firstMonth == null) return emptyList()
        val extended = MonthWindow(window.first.minusMonths(ROLLING_MONTHS - 1L), window.last)
        val monthly = monthlySeries(spending, extended)
        val result = ArrayList<MonthTotal>(window.size)
        for (i in ROLLING_MONTHS - 1 until monthly.size) {
            val month = monthly[i].month
            if (month < firstMonth) continue
            val span = (i - ROLLING_MONTHS + 1..i).filter { monthly[it].month >= firstMonth }
            val sum = span.sumOf { monthly[it].cents }
            result += MonthTotal(month, divideHalfEven(sum, span.size.toLong()))
        }
        return result
    }

    /**
     * The categories of a stacked chart: the [limit] largest of [spending] by total (ties by name),
     * never the real "Ostalo", which is folded into [OTHER] with everything else.
     */
    fun topCategories(spending: Iterable<Expense>, limit: Int = STACK_CATEGORIES): List<String> =
        spending.groupingBy { it.category }.fold(0L) { acc, e -> acc + e.amountCents }
            .filter { it.key != OTHER && it.value > 0 }
            .entries.sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
            .take(limit)
            .map { it.key }

    /**
     * [spending] per period of [periods] (the order of the columns; [periodOf] maps a date to one)
     * split into the [topCategories] and [OTHER] (only when the rest is not zero).
     */
    fun <P> stack(
        spending: List<Expense>,
        periods: List<P>,
        periodOf: (LocalDate) -> P,
        limit: Int = STACK_CATEGORIES,
    ): StackedSeries<P> {
        val top = topCategories(spending, limit)
        val index = periods.withIndex().associate { (i, p) -> p to i }
        val slot = top.withIndex().associate { (i, c) -> c to i }
        val sums = Array(periods.size) { LongArray(top.size + 1) }
        for (e in spending) {
            val p = index[periodOf(e.date)] ?: continue
            sums[p][slot[e.category] ?: top.size] += e.amountCents
        }
        val hasOther = sums.any { it[top.size] > 0 }
        val categories = if (hasOther) top + OTHER else top
        return StackedSeries(
            categories = categories,
            columns = periods.mapIndexed { i, p -> StackColumn(p, sums[i].take(categories.size)) },
        )
    }

    /** The quarters that overlap [window], chronological (the first and last can be partial). */
    fun quarters(window: MonthWindow): List<Quarter> {
        val last = Quarter.of(window.last)
        return generateSequence(Quarter.of(window.first)) { it.next() }.takeWhile { it <= last }.toList()
    }

    /** The top [limit] categories of [inWindow] by total, each with its monthly series. */
    fun categorySeries(
        inWindow: List<Expense>,
        window: MonthWindow,
        firstMonth: YearMonth?,
        limit: Int = SMALL_MULTIPLES,
    ): List<CategorySeries> {
        val averagedMonths = if (firstMonth == null) 0 else window.months.count { it >= firstMonth }
        return inWindow.groupingBy { it.category }.fold(0L) { acc, e -> acc + e.amountCents }
            .filter { it.value > 0 }
            .entries.sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
            .take(limit)
            .map { (category, total) ->
                CategorySeries(
                    category = category,
                    months = monthlySeries(inWindow, window, category),
                    totalCents = total,
                    monthlyAverageCents = if (averagedMonths == 0) 0L else divideHalfEven(total, averagedMonths.toLong()),
                )
            }
    }

    /**
     * Each calendar year from the window's first to its last (the newest [MAX_YEARS]), January to
     * December or, in the window's last year, to its last month; [spending] is pre-filtered and
     * not clipped to the window, so every year is complete.
     */
    fun cumulativeByYear(spending: Iterable<Expense>, window: MonthWindow): List<YearCumulative> {
        val years = (maxOf(window.first.year, window.last.year - MAX_YEARS + 1)..window.last.year).toList()
        val sums = years.associateWith { LongArray(12) }
        for (e in spending) {
            val arr = sums[e.date.year] ?: continue
            arr[e.date.monthValue - 1] += e.amountCents
        }
        return years.map { year ->
            val months = if (year == window.last.year) window.last.monthValue else 12
            var running = 0L
            YearCumulative(year, List(months) { m -> running += sums.getValue(year)[m]; running })
        }
    }

    /** See [PayerSeries]; considers every spending expense of either person. */
    fun payers(expenses: Iterable<Expense>, window: MonthWindow): PayerSeries {
        val n = window.size
        val paid = Person.entries.associateWith { LongArray(n) }
        val half = LongArray(n)
        val fullFor = Person.entries.associateWith { LongArray(n) }
        for (e in expenses) {
            if (!Statistics.isSpending(e) || e.date !in window) continue
            val i = window.indexOf(YearMonth.from(e.date))
            paid.getValue(e.paidBy)[i] += e.amountCents
            when (e.split) {
                SplitType.HALF -> half[i] += e.amountCents
                SplitType.FULL_FOR_OTHER -> fullFor.getValue(e.paidBy.other)[i] += e.amountCents
            }
        }
        val months = window.months
        fun series(values: LongArray) = months.mapIndexed { i, m -> MonthTotal(m, values[i]) }
        val lovelHalf = LongArray(n) { halveHalfEven(half[it]) }
        return PayerSeries(
            paid = Person.entries.associateWith { series(paid.getValue(it)) },
            share = mapOf(
                Person.LOVEL to series(LongArray(n) { lovelHalf[it] + fullFor.getValue(Person.LOVEL)[it] }),
                Person.MARINA to series(LongArray(n) { half[it] - lovelHalf[it] + fullFor.getValue(Person.MARINA)[it] }),
            ),
        )
    }
}

internal fun monthsBetween(from: YearMonth, to: YearMonth): Int =
    (to.year - from.year) * 12 + (to.monthValue - from.monthValue)
