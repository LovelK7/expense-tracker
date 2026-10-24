package hr.ml.expensetracker.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hr.ml.expensetracker.domain.Expense
import hr.ml.expensetracker.domain.ExpenseRepository
import hr.ml.expensetracker.domain.Person
import hr.ml.expensetracker.domain.Statistics
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

/** The selected period: [mode] plus the month and year the arrows move through. */
data class StatsSelection(val mode: StatsMode, val month: YearMonth, val year: Int = month.year)

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
)

data class MonthRow(
    val month: YearMonth,
    val cents: Long,
    /** Bar length relative to the largest shown month, 0..1. */
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

data class StatsUiState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val selection: StatsSelection,
    /** Sve mode: the first and last month with expenses (null when there are none). */
    val allFrom: YearMonth? = null,
    val allTo: YearMonth? = null,
    val canGoPrevious: Boolean = false,
    val canGoNext: Boolean = false,
    val expenseCount: Int = 0,
    val totalCents: Long = 0,
    val people: List<PersonRow> = emptyList(),
    /** Largest first. */
    val categories: List<CategoryRow> = emptyList(),
    /** Godina and Sve modes only; newest first. */
    val months: List<MonthRow> = emptyList(),
    /** Sve mode: there are months older than the [ALL_TIME_MONTHS] shown. */
    val olderMonthsHidden: Boolean = false,
) {
    val mode: StatsMode get() = selection.mode
    val empty: Boolean get() = !loading && !error && expenseCount == 0
}

/** Statistics of one period, before the UI-only bits (which categories are expanded). */
private data class PeriodStats(
    val selection: StatsSelection,
    val stats: Statistics?,
    /** The period's expenses per category (unsorted). */
    val byCategory: Map<String, List<Expense>>,
    val canGoPrevious: Boolean,
    val canGoNext: Boolean,
)

private val NEWEST_FIRST = compareByDescending<Expense> { it.date }.thenByDescending { it.createdAt }

/**
 * The "Statistika" tab (the legacy "PIVOT" sheet): totals per category, per month and per person
 * for a month, a year or all time. Settlements are not spending and are not part of it.
 * The statistics are computed on [computeDispatcher] whenever the expenses or the period change.
 */
class StatisticsViewModel(
    expenses: ExpenseRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
    computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val selection = MutableStateFlow(StatsSelection(StatsMode.MONTH, YearMonth.now(clock)))
    private val expanded = MutableStateFlow<Set<String>>(emptySet())

    val state: StateFlow<StatsUiState> =
        combine(expenses.observeAll(), selection, ::computePeriod)
            .conflate()
            .combine(expanded, ::toUiState)
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

    private fun computePeriod(all: List<Expense>, sel: StatsSelection): PeriodStats {
        val live = all.filterNot { it.deleted }
        // Navigation runs from the earliest data (or today) to the latest data (or today).
        val today = LocalDate.now(clock)
        var earliest = today
        var latest = today
        for (e in live) {
            if (e.date.isBefore(earliest)) earliest = e.date
            if (e.date.isAfter(latest)) latest = e.date
        }

        val (inRange, stats) = when (sel.mode) {
            StatsMode.MONTH -> inRange(live, sel.month.atDay(1), sel.month.atEndOfMonth())
            StatsMode.YEAR -> inRange(live, LocalDate.of(sel.year, 1, 1), LocalDate.of(sel.year, 12, 31))
            StatsMode.ALL -> live to Statistics.forAll(live)
        }
        val (canPrev, canNext) = when (sel.mode) {
            StatsMode.MONTH -> (sel.month > YearMonth.from(earliest)) to (sel.month < YearMonth.from(latest))
            StatsMode.YEAR -> (sel.year > earliest.year) to (sel.year < latest.year)
            StatsMode.ALL -> false to false
        }
        return PeriodStats(sel, stats, inRange.groupBy { it.category }, canPrev, canNext)
    }

    private fun inRange(live: List<Expense>, from: LocalDate, to: LocalDate): Pair<List<Expense>, Statistics> {
        val rows = live.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
        return rows to Statistics.compute(rows, from, to)
    }

    private fun toUiState(p: PeriodStats, expandedCategories: Set<String>): StatsUiState {
        val stats = p.stats
        val base = StatsUiState(
            loading = false,
            selection = p.selection,
            canGoPrevious = p.canGoPrevious,
            canGoNext = p.canGoNext,
        )
        if (stats == null || stats.expenseCount == 0) return base
        val total = stats.totalCents

        val largestCategory = stats.byCategory.values.maxOrNull() ?: 0L
        val categories = stats.byCategory.map { (name, cents) ->
            val open = name in expandedCategories
            CategoryRow(
                category = name,
                cents = cents,
                percent = if (total > 0) cents * 100.0 / total else 0.0,
                barFraction = fraction(cents, largestCategory),
                expanded = open,
                expenses = if (open) p.byCategory[name].orEmpty().sortedWith(NEWEST_FIRST) else emptyList(),
            )
        }

        var olderHidden = false
        val months = when (p.selection.mode) {
            StatsMode.MONTH -> emptyList()
            StatsMode.YEAR, StatsMode.ALL -> {
                val newestFirst = stats.byMonthContinuous().entries.reversed()
                val shown = if (p.selection.mode == StatsMode.ALL) {
                    olderHidden = newestFirst.size > ALL_TIME_MONTHS
                    newestFirst.take(ALL_TIME_MONTHS)
                } else {
                    newestFirst
                }
                val largest = shown.maxOfOrNull { it.value } ?: 0L
                shown.map { (month, cents) -> MonthRow(month, cents, fraction(cents, largest)) }
            }
        }

        return base.copy(
            allFrom = if (p.selection.mode == StatsMode.ALL) YearMonth.from(stats.from) else null,
            allTo = if (p.selection.mode == StatsMode.ALL) YearMonth.from(stats.to) else null,
            expenseCount = stats.expenseCount,
            totalCents = total,
            people = Person.entries.map { person ->
                val share = stats.shareByPerson[person] ?: 0L
                PersonRow(person, share, stats.paidByPerson[person] ?: 0L, fraction(share, total))
            },
            categories = categories,
            months = months,
            olderMonthsHidden = olderHidden,
        )
    }

    private fun fraction(part: Long, whole: Long): Float =
        if (whole <= 0L) 0f else (part.toDouble() / whole).toFloat().coerceIn(0f, 1f)
}
