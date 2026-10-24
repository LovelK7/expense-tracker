package hr.ml.expensetracker.ui.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hr.ml.expensetracker.domain.Categories
import hr.ml.expensetracker.domain.Expense
import hr.ml.expensetracker.domain.ExpenseRepository
import hr.ml.expensetracker.domain.Money
import hr.ml.expensetracker.domain.Person
import hr.ml.expensetracker.domain.RecurringRepository
import hr.ml.expensetracker.domain.RecurringTemplate
import hr.ml.expensetracker.domain.SplitType
import hr.ml.expensetracker.session.CurrentPerson
import hr.ml.expensetracker.ui.common.acceptAmountInput
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Matches the limits `firestore.rules` puts on a template. */
private const val MAX_DESCRIPTION = 1000

/** The add/edit form of one template. */
data class TemplateForm(
    /** Id of the template being edited; null for a new one. */
    val editingId: String? = null,
    val name: String = "",
    val category: String? = null,
    val description: String = "",
    val amountText: String = "",
    val paidBy: Person,
    val split: SplitType = SplitType.HALF,
    val dayText: String,
    val active: Boolean = true,
    /** Picker order: most used first, plus the template's own category when it is not offered. */
    val categories: List<String> = Categories.FOR_PICKER,
    /** The category grid is open until a category is chosen (see the expense form). */
    val categoriesExpanded: Boolean = true,
    /** Opened by "Spremi kao predložak": closing the form closes the whole screen. */
    val fromExpense: Boolean = false,
) {
    val amountCents: Long? get() = Money.parseCents(amountText)

    /** The day of the month, or null when it is not 1..31. */
    val dayOfMonth: Int? get() = dayText.toIntOrNull()?.takeIf { it in RecurringTemplate.DAYS }

    val canSave: Boolean
        get() = name.isNotBlank() && category != null && (amountCents ?: 0L) > 0L && dayOfMonth != null
}

/**
 * "Predlošci": the list of templates (with the active toggle) and their add/edit form. Writes go
 * to [recurring] like every other record (fire-and-forget, soft delete). [openFromExpense]
 * starts a new template from an expense ("Spremi kao predložak").
 */
class TemplatesViewModel(
    private val recurring: RecurringRepository,
    private val expenses: ExpenseRepository,
    private val currentPerson: CurrentPerson,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {
    /** Every non-deleted template by name; null until loaded. */
    val templates: StateFlow<List<RecurringTemplate>?> = recurring.observeAll()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _form = MutableStateFlow<TemplateForm?>(null)

    /** The open form; null shows the list. */
    val form: StateFlow<TemplateForm?> = _form.asStateFlow()

    private var editing: RecurringTemplate? = null
    private var pickerOrder: List<String> = Categories.FOR_PICKER

    init {
        viewModelScope.launch {
            expenses.observeAll()
                .map { Categories.orderByUsage(it) }
                .distinctUntilChanged()
                .catch { }
                .collect { order ->
                    pickerOrder = order
                    _form.update { it?.copy(categories = pickerFor(it.category)) }
                }
        }
    }

    private fun pickerFor(category: String?): List<String> =
        if (category == null || category in pickerOrder) pickerOrder else listOf(category) + pickerOrder

    private fun blank() = TemplateForm(
        paidBy = currentPerson.get() ?: Person.LOVEL,
        dayText = LocalDate.now(clock).dayOfMonth.toString(),
        categories = pickerOrder,
    )

    /** Back to the list, with no form open (the screen was just opened). */
    fun showList() {
        editing = null
        _form.value = null
    }

    fun openNew() {
        editing = null
        _form.value = blank()
    }

    fun openEdit(template: RecurringTemplate) {
        editing = template
        _form.value = TemplateForm(
            editingId = template.id,
            name = template.name,
            category = template.category,
            description = template.description,
            amountText = Money.formatInput(template.amountCents),
            paidBy = template.paidBy,
            split = template.split,
            dayText = template.dayOfMonth.toString(),
            active = template.active,
            categories = pickerFor(template.category),
            categoriesExpanded = false,
        )
    }

    /**
     * A new template prefilled from the expense [expenseId]: name = its description (or its
     * category when there is none), day = its day of the month. An empty new form when the
     * expense is gone.
     */
    fun openFromExpense(expenseId: String) {
        editing = null
        _form.value = null
        viewModelScope.launch {
            val e = expenses.observeAll().catch { emit(emptyList()) }.first().firstOrNull { it.id == expenseId }
            _form.value = e?.let(::fromExpense) ?: blank().copy(fromExpense = true)
        }
    }

    private fun fromExpense(e: Expense) = blank().copy(
        name = e.description.trim().ifEmpty { e.category }.take(RecurringTemplate.MAX_NAME),
        category = e.category,
        description = e.description.trim().take(MAX_DESCRIPTION),
        amountText = Money.formatInput(e.amountCents),
        paidBy = e.paidBy,
        split = e.split,
        dayText = e.date.dayOfMonth.toString(),
        categories = pickerFor(e.category),
        categoriesExpanded = false,
        fromExpense = true,
    )

    /** Closes the form without saving; true when the whole screen should close too. */
    fun closeForm(): Boolean {
        val closeScreen = _form.value?.fromExpense == true
        showList()
        return closeScreen
    }

    fun onNameChange(text: String) = _form.update { it?.copy(name = text.take(RecurringTemplate.MAX_NAME)) }

    fun onCategorySelect(category: String) = _form.update { it?.copy(category = category, categoriesExpanded = false) }

    fun expandCategories() = _form.update { it?.copy(categoriesExpanded = true) }

    fun onDescriptionChange(text: String) = _form.update { it?.copy(description = text.take(MAX_DESCRIPTION)) }

    fun onAmountChange(text: String) {
        val accepted = acceptAmountInput(text) ?: return
        _form.update { it?.copy(amountText = accepted) }
    }

    fun onPaidByChange(person: Person) = _form.update { it?.copy(paidBy = person) }

    fun onSplitChange(split: SplitType) = _form.update { it?.copy(split = split) }

    /** Digits only, at most two. */
    fun onDayChange(text: String) {
        if (text.length > 2 || !text.all(Char::isDigit)) return
        _form.update { it?.copy(dayText = text) }
    }

    fun onActiveChange(active: Boolean) = _form.update { it?.copy(active = active) }

    /** Writes the template and closes the form; false (nothing written) when the form is not valid. */
    fun save(): Boolean {
        val f = _form.value ?: return false
        val cents = f.amountCents
        val category = f.category
        val day = f.dayOfMonth
        if (!f.canSave || cents == null || category == null || day == null) return false
        val now = clock.instant()
        if (f.editingId != null) {
            // The newest stored version keeps changes made meanwhile (e.g. a month skipped on the other phone).
            val original = templates.value?.firstOrNull { it.id == f.editingId } ?: editing ?: return false
            recurring.update(
                original.copy(
                    name = f.name.trim(),
                    category = category,
                    description = f.description.trim(),
                    amountCents = cents,
                    paidBy = f.paidBy,
                    split = f.split,
                    dayOfMonth = day,
                    active = f.active,
                    updatedAt = now,
                ),
            )
        } else {
            val me = currentPerson.get() ?: return false
            recurring.add(
                RecurringTemplate(
                    id = recurring.newId(),
                    name = f.name.trim(),
                    category = category,
                    description = f.description.trim(),
                    amountCents = cents,
                    paidBy = f.paidBy,
                    split = f.split,
                    dayOfMonth = day,
                    active = f.active,
                    skippedPeriods = emptySet(),
                    createdAt = now,
                    updatedAt = now,
                    createdBy = me,
                ),
            )
        }
        showList()
        return true
    }

    /** The list's switch. */
    fun setActive(template: RecurringTemplate, active: Boolean) {
        if (template.active == active) return
        recurring.update(template.copy(active = active, updatedAt = clock.instant()))
    }

    /** Soft-deletes the template being edited and closes the form; returns it for [undoDelete]. */
    fun deleteEditing(): RecurringTemplate? {
        val t = editing ?: return null
        recurring.softDelete(t.id)
        showList()
        return t
    }

    /** Writes [template] back as it was before the delete, with a fresh `updatedAt`. */
    fun undoDelete(template: RecurringTemplate) {
        recurring.update(template.copy(deleted = false, updatedAt = clock.instant()))
    }
}
