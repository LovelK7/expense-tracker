package hr.ml.expensetracker.ui.expense

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hr.ml.expensetracker.domain.Categories
import hr.ml.expensetracker.domain.Expense
import hr.ml.expensetracker.domain.ExpenseRepository
import hr.ml.expensetracker.domain.Money
import hr.ml.expensetracker.domain.Person
import hr.ml.expensetracker.domain.SplitType
import hr.ml.expensetracker.session.CurrentPerson
import hr.ml.expensetracker.ui.common.acceptAmountInput
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Matches the 1000-character limit `firestore.rules` puts on `description`. */
private const val MAX_DESCRIPTION = 1000

data class ExpenseFormState(
    val amountText: String = "",
    val category: String? = null,
    val description: String = "",
    val paidBy: Person = Person.LOVEL,
    val split: SplitType = SplitType.HALF,
    val date: LocalDate,
    val today: LocalDate,
    val categories: List<String> = Categories.FOR_PICKER,
    /** Id of the expense being edited; null for a new one. */
    val editingId: String? = null,
    /** Editing only: false until the expense has been looked up. */
    val loaded: Boolean = true,
    /** Editing only: the expense no longer exists (deleted meanwhile). */
    val missing: Boolean = false,
) {
    val amountCents: Long? get() = Money.parseCents(amountText)

    val canSave: Boolean get() = loaded && (amountCents ?: 0L) > 0L && category != null
}

/**
 * The "Dodaj trošak" form, also used to edit an existing expense ([startEdit]). Saving creates
 * the expense with `createdBy` = the signed-in person, or updates it keeping its id, `createdBy`
 * and `createdAt`.
 */
class ExpenseFormViewModel(
    private val expenses: ExpenseRepository,
    private val currentPerson: CurrentPerson,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {
    private var editing: Expense? = null
    private val _state = MutableStateFlow(blank())
    val state: StateFlow<ExpenseFormState> = _state.asStateFlow()

    private fun blank(today: LocalDate = LocalDate.now(clock)) =
        ExpenseFormState(paidBy = currentPerson.get() ?: Person.LOVEL, date = today, today = today)

    /** Loads the expense [id] into the form; a no-op when it is already loaded (e.g. after rotation). */
    fun startEdit(id: String) {
        if (_state.value.editingId == id) return
        editing = null
        _state.value = blank().copy(editingId = id, loaded = false)
        viewModelScope.launch {
            val expense = expenses.observeAll().first().firstOrNull { it.id == id }
            if (_state.value.editingId != id) return@launch
            if (expense == null) {
                _state.update { it.copy(missing = true) }
                return@launch
            }
            editing = expense
            _state.update {
                it.copy(
                    amountText = Money.formatInput(expense.amountCents),
                    category = expense.category,
                    description = expense.description,
                    paidBy = expense.paidBy,
                    split = expense.split,
                    date = expense.date,
                    // A legacy category outside the picker list (e.g. "Prijenos") stays selectable.
                    categories = if (expense.category in Categories.FOR_PICKER) {
                        Categories.FOR_PICKER
                    } else {
                        listOf(expense.category) + Categories.FOR_PICKER
                    },
                    loaded = true,
                )
            }
        }
    }

    /** Called when the add form is shown: an untouched form picks up a new "today" after midnight. */
    fun resetIfPristine() {
        if (_state.value == blank(_state.value.today)) _state.value = blank()
    }

    /** Back to an empty new-expense form. */
    fun reset() {
        editing = null
        _state.value = blank()
    }

    fun onAmountChange(text: String) {
        val accepted = acceptAmountInput(text) ?: return
        _state.update { it.copy(amountText = accepted) }
    }

    fun onCategorySelect(category: String) = _state.update { it.copy(category = category) }

    fun onDescriptionChange(text: String) = _state.update { it.copy(description = text.take(MAX_DESCRIPTION)) }

    fun onPaidByChange(person: Person) = _state.update { it.copy(paidBy = person) }

    fun onSplitChange(split: SplitType) = _state.update { it.copy(split = split) }

    fun onDateChange(date: LocalDate) = _state.update { it.copy(date = date) }

    /** Writes the expense and resets the form; false (nothing written) when the form is not valid. */
    fun save(): Boolean {
        val s = _state.value
        val cents = s.amountCents
        val category = s.category
        if (!s.canSave || cents == null || category == null) return false
        val now = clock.instant()
        val original = editing
        if (s.editingId != null) {
            if (original == null) return false
            expenses.update(
                original.copy(
                    date = s.date,
                    category = category,
                    description = s.description.trim(),
                    amountCents = cents,
                    paidBy = s.paidBy,
                    split = s.split,
                    updatedAt = now,
                ),
            )
        } else {
            val me = currentPerson.get() ?: return false
            expenses.add(
                Expense(
                    id = expenses.newId(),
                    date = s.date,
                    category = category,
                    description = s.description.trim(),
                    amountCents = cents,
                    paidBy = s.paidBy,
                    split = s.split,
                    createdAt = now,
                    updatedAt = now,
                    createdBy = me,
                ),
            )
        }
        reset()
        return true
    }
}
