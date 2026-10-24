package hr.ml.expensetracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hr.ml.expensetracker.domain.BalanceCalculator
import hr.ml.expensetracker.domain.Expense
import hr.ml.expensetracker.domain.ExpenseRepository
import hr.ml.expensetracker.domain.Money
import hr.ml.expensetracker.domain.Person
import hr.ml.expensetracker.domain.Settlement
import hr.ml.expensetracker.domain.SettlementRepository
import hr.ml.expensetracker.session.CurrentPerson
import hr.ml.expensetracker.ui.common.acceptAmountInput
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import kotlin.math.absoluteValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** Matches the 1000-character limit `firestore.rules` puts on `note`. */
private const val MAX_NOTE = 1000

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

data class HomeUiState(
    val loading: Boolean = true,
    /** Positive: Lovel owes Marina. Negative: Marina owes Lovel. */
    val balanceCents: Long = 0,
    val expenseCount: Int = 0,
    val error: Boolean = false,
    /** Expenses and settlements interleaved, grouped by day, newest day first. */
    val days: List<HomeDay> = emptyList(),
    val today: LocalDate = LocalDate.of(1970, 1, 1),
    val settleUp: SettleUpDraft? = null,
)

private val NEWEST_FIRST = compareByDescending<HomeRecord> { it.date }.thenByDescending { it.createdAt }

class HomeViewModel(
    private val expenses: ExpenseRepository,
    private val settlements: SettlementRepository,
    private val currentPerson: CurrentPerson = CurrentPerson { null },
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {
    private val settleDraft = MutableStateFlow<SettleUpDraft?>(null)

    val state: StateFlow<HomeUiState> =
        combine(expenses.observeAll(), settlements.observeAll()) { e, s -> buildState(e, s) }
            .catch { emit(HomeUiState(loading = false, error = true)) }
            .combine(settleDraft) { base, draft -> base.copy(settleUp = draft) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private fun buildState(e: List<Expense>, s: List<Settlement>): HomeUiState {
        val live = e.filterNot { it.deleted }
        val records = live.map { HomeRecord.ExpenseItem(it) } +
            s.filterNot { it.deleted }.map { HomeRecord.SettlementItem(it) }
        return HomeUiState(
            loading = false,
            balanceCents = BalanceCalculator.balanceCents(e, s),
            expenseCount = live.size,
            // groupBy keeps the sorted order, so days and their rows are both newest first.
            days = records.sortedWith(NEWEST_FIRST).groupBy { it.date }.map { (date, rows) -> HomeDay(date, rows) },
            today = LocalDate.now(clock),
        )
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
