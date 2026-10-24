package hr.ml.expensetracker.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hr.ml.expensetracker.domain.BalanceCalculator
import hr.ml.expensetracker.domain.Expense
import hr.ml.expensetracker.domain.ExpenseRepository
import hr.ml.expensetracker.domain.Money
import hr.ml.expensetracker.domain.Person
import hr.ml.expensetracker.domain.RecordFilter
import hr.ml.expensetracker.domain.SearchText
import hr.ml.expensetracker.domain.Settlement
import hr.ml.expensetracker.domain.SettlementRepository
import hr.ml.expensetracker.session.CurrentPerson
import hr.ml.expensetracker.ui.common.acceptAmountInput
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.absoluteValue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** Matches the 1000-character limit `firestore.rules` puts on `note`. */
private const val MAX_NOTE = 1000

/** Typing pauses this long before the list is filtered again; clearing the search applies at once. */
const val SEARCH_DEBOUNCE_MS = 150L

/** More than enough for a search; keeps a pasted novel from being matched against every row. */
private const val MAX_QUERY = 100

/** One row of the home list: an expense or a settlement. */
sealed interface HomeRecord {
    /** Unique across both kinds (a list key). */
    val key: String
    val date: LocalDate
    val createdAt: Instant

    data class ExpenseItem(val expense: Expense) : HomeRecord {
        override val key get() = "e:${expense.id}"
        override val date: LocalDate get() = expense.date
        override val createdAt: Instant get() = expense.createdAt
    }

    data class SettlementItem(val settlement: Settlement) : HomeRecord {
        override val key get() = "s:${settlement.id}"
        override val date: LocalDate get() = settlement.date
        override val createdAt: Instant get() = settlement.createdAt
    }
}

/** The records of one calendar day, newest first. */
data class HomeDay(val date: LocalDate, val records: List<HomeRecord>)

/** The open "Podmiri" dialog: [from] pays [to] the (editable) amount. */
data class SettleUpDraft(
    val from: Person,
    val amountText: String,
    val date: LocalDate,
    val note: String = "",
) {
    val to: Person get() = from.other

    val amountCents: Long? get() = Money.parseCents(amountText)

    val canConfirm: Boolean get() = (amountCents ?: 0L) > 0L
}

/**
 * The line above a filtered list: [expenseCount] expenses totalling [totalCents]. Settlements are
 * listed but neither counted nor summed; [recordCount] counts every shown row.
 */
data class FilterSummary(val expenseCount: Int, val totalCents: Long, val recordCount: Int) {
    val noResults: Boolean get() = recordCount == 0
}

data class HomeUiState(
    val loading: Boolean = true,
    /**
     * Positive: Lovel owes Marina. Negative: Marina owes Lovel. Always over every record: the month
     * filter and the search narrow the list only, never the balance.
     */
    val balanceCents: Long = 0,
    /** Every non-deleted expense, regardless of the filter. */
    val expenseCount: Int = 0,
    val error: Boolean = false,
    /** The shown (filtered) expenses and settlements interleaved, grouped by day, newest day first. */
    val days: List<HomeDay> = emptyList(),
    val today: LocalDate = LocalDate.of(1970, 1, 1),
    val settleUp: SettleUpDraft? = null,
    /** The month chips: every month with a record, newest first (plus the selected one, even once emptied). */
    val months: List<YearMonth> = emptyList(),
    /** The month filter in effect; null is "Sve". */
    val selectedMonth: YearMonth? = null,
    /** Set while a month or a search narrows the list; null when everything is shown. */
    val summary: FilterSummary? = null,
)

private val NEWEST_FIRST = compareByDescending<HomeRecord> { it.date }.thenByDescending { it.createdAt }

/** A row with its folded searchable text, computed once per snapshot rather than per keystroke. */
private class IndexedRecord(val record: HomeRecord, val searchText: String)

/** Everything derived from one repository snapshot that does not depend on the filter. */
private class Snapshot(
    val balanceCents: Long,
    val expenseCount: Int,
    /** Newest first. */
    val records: List<IndexedRecord>,
    val months: List<YearMonth>,
)

/**
 * The "Početna" tab: the balance, "Podmiri", and the records, narrowed by an optional month and a
 * text search. Indexing and filtering run on [computeDispatcher]; the month and the search live
 * here, so they survive tab switches and rotation but not a restart.
 */
@OptIn(FlowPreview::class)
class HomeViewModel(
    private val expenses: ExpenseRepository,
    private val settlements: SettlementRepository,
    private val currentPerson: CurrentPerson = CurrentPerson { null },
    private val clock: Clock = Clock.systemDefaultZone(),
    computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val settleDraft = MutableStateFlow<SettleUpDraft?>(null)
    private val selectedMonth = MutableStateFlow<YearMonth?>(null)
    private val query = MutableStateFlow("")
    private val searchOpenFlow = MutableStateFlow(false)

    /** Whether the search field replaces the top bar title. */
    val searchOpen: StateFlow<Boolean> = searchOpenFlow.asStateFlow()

    /**
     * The search field's text. Compose state, written synchronously, so the field never lags
     * behind the keyboard; the list follows it through the debounced [query].
     */
    var searchText by mutableStateOf("")
        private set

    private val filter: Flow<RecordFilter> =
        combine(selectedMonth, query.debounce { if (it.isBlank()) 0L else SEARCH_DEBOUNCE_MS }) { month, q ->
            RecordFilter.of(month, q)
        }.distinctUntilChanged()

    private val snapshot: Flow<Snapshot> =
        combine(expenses.observeAll(), settlements.observeAll(), ::index).conflate()

    val state: StateFlow<HomeUiState> =
        combine(snapshot, filter, ::buildState)
            .conflate()
            .flowOn(computeDispatcher)
            .catch { emit(HomeUiState(loading = false, error = true)) }
            .combine(settleDraft) { base, draft -> base.copy(settleUp = draft) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private fun index(e: List<Expense>, s: List<Settlement>): Snapshot {
        val live = e.filterNot { it.deleted }
        val liveSettlements = s.filterNot { it.deleted }
        val records = live.map { IndexedRecord(HomeRecord.ExpenseItem(it), SearchText.of(it)) } +
            liveSettlements.map { IndexedRecord(HomeRecord.SettlementItem(it), SearchText.of(it)) }
        return Snapshot(
            balanceCents = BalanceCalculator.balanceCents(e, s),
            expenseCount = live.size,
            records = records.sortedWith(compareBy(NEWEST_FIRST) { it.record }),
            months = RecordFilter.months(live, liveSettlements),
        )
    }

    private fun buildState(snap: Snapshot, filter: RecordFilter): HomeUiState {
        val shown = if (filter.isActive) {
            snap.records.mapNotNull { r -> r.record.takeIf { filter.matches(it.date, r.searchText) } }
        } else {
            snap.records.map { it.record }
        }
        val month = filter.month
        return HomeUiState(
            loading = false,
            balanceCents = snap.balanceCents,
            expenseCount = snap.expenseCount,
            // groupBy keeps the sorted order, so days and their rows are both newest first.
            days = shown.groupBy { it.date }.map { (date, rows) -> HomeDay(date, rows) },
            today = LocalDate.now(clock),
            months = if (month == null || month in snap.months) snap.months else (snap.months + month).sortedDescending(),
            selectedMonth = month,
            summary = if (filter.isActive) summarize(shown) else null,
        )
    }

    private fun summarize(shown: List<HomeRecord>): FilterSummary {
        var count = 0
        var total = 0L
        for (r in shown) {
            if (r is HomeRecord.ExpenseItem) {
                count++
                total += r.expense.amountCents
            }
        }
        return FilterSummary(expenseCount = count, totalCents = total, recordCount = shown.size)
    }

    /** Shows only [month]'s records; null shows every month ("Sve"). */
    fun selectMonth(month: YearMonth?) {
        selectedMonth.value = month
    }

    fun openSearch() {
        searchOpenFlow.value = true
    }

    fun onSearchTextChange(text: String) {
        val capped = text.take(MAX_QUERY)
        searchText = capped
        query.value = capped
    }

    fun clearSearch() = onSearchTextChange("")

    /** Closing the search also clears it. */
    fun closeSearch() {
        clearSearch()
        searchOpenFlow.value = false
    }

    /** Opens "Podmiri" with the direction and amount that bring the balance to zero; no-op when settled. */
    fun openSettleUp() {
        val balance = state.value.balanceCents
        if (balance == 0L) return
        settleDraft.value = SettleUpDraft(
            from = if (balance > 0) Person.LOVEL else Person.MARINA,
            amountText = Money.formatInput(balance.absoluteValue),
            date = LocalDate.now(clock),
        )
    }

    fun onSettleAmountChange(text: String) {
        val accepted = acceptAmountInput(text) ?: return
        settleDraft.update { it?.copy(amountText = accepted) }
    }

    fun onSettleNoteChange(text: String) = settleDraft.update { it?.copy(note = text.take(MAX_NOTE)) }

    fun onSettleDateChange(date: LocalDate) = settleDraft.update { it?.copy(date = date) }

    fun dismissSettleUp() {
        settleDraft.value = null
    }

    /** Records the settlement and closes the dialog; false (nothing written) when the amount is not valid. */
    fun confirmSettleUp(): Boolean {
        val draft = settleDraft.value ?: return false
        val cents = draft.amountCents?.takeIf { it > 0 } ?: return false
        val me = currentPerson.get() ?: return false
        val now = clock.instant()
        settlements.add(
            Settlement(
                id = settlements.newId(),
                date = draft.date,
                from = draft.from,
                to = draft.to,
                amountCents = cents,
                note = draft.note.trim(),
                createdAt = now,
                updatedAt = now,
                createdBy = me,
            ),
        )
        settleDraft.value = null
        return true
    }

    /** Soft-deletes [record]; [undoDelete] with the same record brings it back. */
    fun delete(record: HomeRecord) = when (record) {
        is HomeRecord.ExpenseItem -> expenses.softDelete(record.expense.id)
        is HomeRecord.SettlementItem -> settlements.softDelete(record.settlement.id)
    }

    /** Writes [record] back as it was before [delete], not deleted, with a fresh `updatedAt`. */
    fun undoDelete(record: HomeRecord) {
        val now = clock.instant()
        when (record) {
            is HomeRecord.ExpenseItem -> expenses.update(record.expense.copy(deleted = false, updatedAt = now))
            is HomeRecord.SettlementItem -> settlements.update(record.settlement.copy(deleted = false, updatedAt = now))
        }
    }
}
