package hr.ml.expensetracker.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hr.ml.expensetracker.domain.CategoryTrend
import hr.ml.expensetracker.domain.Expense
import hr.ml.expensetracker.domain.ExpenseRepository
import hr.ml.expensetracker.domain.PeriodComparison
import hr.ml.expensetracker.domain.Percent
import hr.ml.expensetracker.domain.Person
import hr.ml.expensetracker.domain.Statistics
import hr.ml.expensetracker.domain.Trends
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

enum class StatsMode { MONTH, YEAR, ALL }

/** "Po mjesecima" in the Sve mode stops after this many months (newest first). */
const val ALL_TIME_MONTHS = 24

/**
 * The selected period: [mode] plus the month and year the arrows move through, and whose
 * spending is shown ([person]: only what that person paid; null: both).
 */
data class StatsSelection(
    val mode: StatsMode,
    val month: YearMonth,
    val year: Int = month.year,
    val person: Person? = null,
)

/** One bar of an expanded category's 12-month trend. */
data class TrendBar(
    val month: YearMonth,
    val cents: Long,
    /** Bar height relative to the window's largest month, 0..1. */
    val fraction: Float,
    /** The month is part of the selected period (drawn stronger). */
    val inPeriod: Boolean,
)

/** The 12-month trend shown above an expanded category's expenses. */
data class CategoryTrendUi(
    /** Chronological. */
    val bars: List<TrendBar>,
    val monthlyAverageCents: Long,
)

data class CategoryRow(
    val category: String,
    val cents: Long,
    /** Share of the period total, 0..100. */
    val percent: Double,
    /** Bar length relative to the largest category, 0..1. */
    val barFraction: Float,
    val expanded: Boolean = false,
    /** This category's expenses in the period, newest first; only filled while [expanded]. */
    val expenses: List<Expense> = emptyList(),
    /** Only filled while [expanded]. */
    val trend: CategoryTrendUi? = null,
)

data class MonthRow(
    val month: YearMonth,
    val cents: Long,
    /** Bar length relative to the largest shown month, 0..1. */
    val barFraction: Float,
)

/** "Po godinama" (Sve mode): one calendar year. */
data class YearRow(
    val year: Int,
    val cents: Long,
    val monthlyAverageCents: Long,
    /** Bar length relative to the largest year, 0..1. */
    val barFraction: Float,
)

data class PersonRow(
    val person: Person,
    /** What this person owes toward the period total (see [Statistics.shareByPerson]). */
    val shareCents: Long,
    val paidCents: Long,
    /** [shareCents] as a part of the period total, 0..1. */
    val shareFraction: Float,
)

/** What a period is compared with, for the summary card's comparison lines. */
sealed interface ComparisonReference {
    /** "u odnosu na rujan 2026." */
    data class Month(val month: YearMonth) : ComparisonReference

    /** "u odnosu na 2025." or, with [toDate], "u odnosu na 2025. (do danas)". */
    data class Year(val year: Int, val toDate: Boolean) : ComparisonReference
}

/** "+12 % u odnosu na rujan 2026.": only built when the reference period has spending. */
data class ComparisonRow(val changePercent: Int, val reference: ComparisonReference)

data class StatsUiState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val selection: StatsSelection,
    /** Sve mode: the first and last month with expenses (null when there are none). */
    val allFrom: YearMonth? = null,
    val allTo: YearMonth? = null,
    val canGoPrevious: Boolean = false,
    val canGoNext: Boolean = false,
    /** Spending expenses in the period (transfers not counted). */
    val expenseCount: Int = 0,
    /** Spending in the period; with a person selected, what that person paid. */
    val totalCents: Long = 0,
    /** Person view: [totalCents] as a whole percentage of both people's spending in the period. */
    val personPercent: Int? = null,
    /** Transfers ("Prijenos") in the period, left out of everything else; 0 hides the line. */
    val transferCents: Long = 0,
    /** Mjesec: previous month, then the same month a year earlier; Godina: the previous year. */
    val comparisons: List<ComparisonRow> = emptyList(),
    /** Both people only (empty in the person view). */
    val people: List<PersonRow> = emptyList(),
    /** Largest first. */
    val categories: List<CategoryRow> = emptyList(),
    /** Godina and Sve modes only; newest first. */
    val months: List<MonthRow> = emptyList(),
    /** Sve mode: there are months older than the [ALL_TIME_MONTHS] shown. */
    val olderMonthsHidden: Boolean = false,
    /** Sve mode only; newest first. */
    val years: List<YearRow> = emptyList(),
) {
    val mode: StatsMode get() = selection.mode
    val person: Person? get() = selection.person
    val empty: Boolean get() = !loading && !error && expenseCount == 0
}

private val NEWEST_FIRST = compareByDescending<Expense> { it.date }.thenByDescending { it.createdAt }

/**
 * The "Statistika" tab (the legacy "PIVOT" sheet): totals per category, per month, per year and
 * per person for a month, a year or all time, with period comparisons and category trends.
 * Settlements and transfers ("Prijenos") are not spending and are not part of it.
 * Everything is recomputed on [computeDispatcher], once per change of the expenses, the selection
 * or the expanded categories.
 */
class StatisticsViewModel(
    expenses: ExpenseRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
    computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val selection = MutableStateFlow(StatsSelection(StatsMode.MONTH, YearMonth.now(clock)))
    private val expanded = MutableStateFlow<Set<String>>(emptySet())

    val state: StateFlow<StatsUiState> =
        combine(expenses.observeAll(), selection, expanded, ::compute)
            .conflate()
            .flowOn(computeDispatcher)
            .catch { emit(StatsUiState(loading = false, error = true, selection = selection.value)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState(selection = selection.value))

    fun setMode(mode: StatsMode) = selection.update { s ->
        when {
            s.mode == mode -> s
            // Coming back from a year the month is not in: pick the year's most relevant month.
            mode == StatsMode.MONTH && s.month.year != s.year -> s.copy(mode = mode, month = defaultMonthOf(s.year))
            else -> s.copy(mode = mode)
        }
    }

    /** Whose spending is shown: one person's payments, or both (null). */
    fun setPerson(person: Person?) = selection.update { it.copy(person = person) }

    fun previous() {
        if (state.value.canGoPrevious) selection.update { it.shifted(-1) }
    }

    fun next() {
        if (state.value.canGoNext) selection.update { it.shifted(+1) }
    }

    fun toggleCategory(category: String) = expanded.update { if (category in it) it - category else it + category }

    private fun StatsSelection.shifted(by: Long): StatsSelection = when (mode) {
        StatsMode.MONTH -> month.plusMonths(by).let { copy(month = it, year = it.year) }
        StatsMode.YEAR -> copy(year = year + by.toInt())
        StatsMode.ALL -> this
    }

    /** The current month in the current year, December for past years, January for future ones. */
    private fun defaultMonthOf(year: Int): YearMonth {
        val now = YearMonth.now(clock)
        return when {
            year == now.year -> now
            year < now.year -> YearMonth.of(year, 12)
            else -> YearMonth.of(year, 1)
        }
    }

    private fun compute(all: List<Expense>, sel: StatsSelection, expandedCategories: Set<String>): StatsUiState {
        val live = all.filterNot { it.deleted }
        val today = LocalDate.now(clock)
        // Navigation runs from the earliest data (or today) to the latest data (or today).
        var earliest = today
        var latest = today
        for (e in live) {
            if (e.date.isBefore(earliest)) earliest = e.date
            if (e.date.isAfter(latest)) latest = e.date
        }
        val (canPrev, canNext) = when (sel.mode) {
            StatsMode.MONTH -> (sel.month > YearMonth.from(earliest)) to (sel.month < YearMonth.from(latest))
            StatsMode.YEAR -> (sel.year > earliest.year) to (sel.year < latest.year)
            StatsMode.ALL -> false to false
        }
        val base = StatsUiState(loading = false, selection = sel, canGoPrevious = canPrev, canGoNext = canNext)

        val range: ClosedRange<LocalDate> = when (sel.mode) {
            StatsMode.MONTH -> sel.month.atDay(1)..sel.month.atEndOfMonth()
            StatsMode.YEAR -> LocalDate.of(sel.year, 1, 1)..LocalDate.of(sel.year, 12, 31)
            StatsMode.ALL -> Statistics.liveRange(live) ?: return base
        }
        val person = sel.person
        val stats = Statistics.compute(live, range.start, range.endInclusive, person)
        val withTransfers = base.copy(
            allFrom = if (sel.mode == StatsMode.ALL) YearMonth.from(range.start) else null,
            allTo = if (sel.mode == StatsMode.ALL) YearMonth.from(range.endInclusive) else null,
            transferCents = stats.transferCents,
        )
        if (stats.expenseCount == 0) return withTransfers
        val total = stats.totalCents

        val personPercent = if (person == null) {
            null
        } else {
            Percent.of(total, Statistics.compute(live, range.start, range.endInclusive).totalCents)
        }

        val comparisons = when (sel.mode) {
            StatsMode.MONTH -> listOfNotNull(
                Trends.previousMonth(live, sel.month, person).toRow(ComparisonReference.Month(sel.month.minusMonths(1))),
                Trends.sameMonthLastYear(live, sel.month, person).toRow(ComparisonReference.Month(sel.month.minusYears(1))),
            )
            StatsMode.YEAR -> Trends.previousYear(live, sel.year, today, person).let { c ->
                listOfNotNull(c.toRow(ComparisonReference.Year(sel.year - 1, toDate = c.yearToDate)))
            }
            StatsMode.ALL -> emptyList()
        }

        // The category trends end with the period, but never after the data or today.
        val dataEnd = maxOf(YearMonth.from(today), YearMonth.from(latest))
        val trendEnd = minOf(YearMonth.from(range.endInclusive), dataEnd)
        val periodMonths = YearMonth.from(range.start)..YearMonth.from(range.endInclusive)
        val inPeriodByCategory: Map<String, List<Expense>> by lazy {
            live.filter { Statistics.isSpending(it, person) && it.date in range }.groupBy { it.category }
        }

        val largestCategory = stats.byCategory.values.maxOrNull() ?: 0L
        val categories = stats.byCategory.map { (name, cents) ->
            val open = name in expandedCategories
            CategoryRow(
                category = name,
                cents = cents,
                percent = if (total > 0) cents * 100.0 / total else 0.0,
                barFraction = fraction(cents, largestCategory),
                expanded = open,
                expenses = if (open) inPeriodByCategory[name].orEmpty().sortedWith(NEWEST_FIRST) else emptyList(),
                trend = if (open) Trends.categoryTrend(live, name, trendEnd, person).toUi(periodMonths) else null,
            )
        }

        var olderHidden = false
        val months = when (sel.mode) {
            StatsMode.MONTH -> emptyList()
            StatsMode.YEAR, StatsMode.ALL -> {
                val newestFirst = stats.byMonthContinuous().entries.reversed()
                val shown = if (sel.mode == StatsMode.ALL) {
                    olderHidden = newestFirst.size > ALL_TIME_MONTHS
                    newestFirst.take(ALL_TIME_MONTHS)
                } else {
                    newestFirst
                }
                val largest = shown.maxOfOrNull { it.value } ?: 0L
                shown.map { (month, cents) -> MonthRow(month, cents, fraction(cents, largest)) }
            }
        }

        val years = if (sel.mode == StatsMode.ALL) {
            val rows = Trends.byYear(live, today, person)
            val largest = rows.maxOfOrNull { it.cents } ?: 0L
            rows.map { YearRow(it.year, it.cents, it.monthlyAverageCents, fraction(it.cents, largest)) }
        } else {
            emptyList()
        }

        return withTransfers.copy(
            expenseCount = stats.expenseCount,
            totalCents = total,
            personPercent = personPercent,
            comparisons = comparisons,
            people = if (person != null) {
                emptyList()
            } else {
                Person.entries.map { p ->
                    val share = stats.shareByPerson[p] ?: 0L
                    PersonRow(p, share, stats.paidByPerson[p] ?: 0L, fraction(share, total))
                }
            },
            categories = categories,
            months = months,
            olderMonthsHidden = olderHidden,
            years = years,
        )
    }

    /** A comparison line, or null when the reference period has no spending to compare with. */
    private fun PeriodComparison.toRow(reference: ComparisonReference): ComparisonRow? =
        changePercent?.let { ComparisonRow(it, reference) }

    private fun CategoryTrend.toUi(period: ClosedRange<YearMonth>): CategoryTrendUi {
        val largest = months.maxOfOrNull { it.cents } ?: 0L
        return CategoryTrendUi(
            bars = months.map { m -> TrendBar(m.month, m.cents, fraction(m.cents, largest), m.month in period) },
            monthlyAverageCents = monthlyAverageCents,
        )
    }

    private fun fraction(part: Long, whole: Long): Float =
        if (whole <= 0L) 0f else (part.toDouble() / whole).toFloat().coerceIn(0f, 1f)
}
