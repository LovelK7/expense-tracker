package hr.ml.expensetracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hr.ml.expensetracker.domain.DueItem
import hr.ml.expensetracker.domain.ExpenseRepository
import hr.ml.expensetracker.domain.Periods
import hr.ml.expensetracker.domain.RecurringRepository
import hr.ml.expensetracker.domain.RecurringSchedule
import hr.ml.expensetracker.domain.RecurringTemplate
import hr.ml.expensetracker.session.CurrentPerson
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * The "Dospjelo" cards on Početna: every template due this month or overdue from last month
 * ([RecurringSchedule.due]), live from both repositories. "Dodaj" posts the expense under its
 * deterministic id, so a card disappears as soon as its expense exists (on either phone);
 * "Preskoči" adds the month to the template's skipped periods.
 */
class DueViewModel(
    private val recurring: RecurringRepository,
    private val expenses: ExpenseRepository,
    private val currentPerson: CurrentPerson,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {
    private val templates: StateFlow<List<RecurringTemplate>> = recurring.observeAll()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Due items, oldest month first. Empty while loading or when the data cannot be read. */
    val due: StateFlow<List<DueItem>> =
        combine(templates, expenses.observeAll()) { t, e -> RecurringSchedule.due(t, e, LocalDate.now(clock), clock.zone) }
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Posts [item]'s expense, `createdBy` = the signed-in person; false when nobody is signed in. */
    fun post(item: DueItem): Boolean {
        val me = currentPerson.get() ?: return false
        expenses.addOrRestore(RecurringSchedule.expenseFor(item, me, clock.instant()))
        return true
    }

    /** Marks [item]'s month skipped; [undoSkip] with the same item reverts it. */
    fun skip(item: DueItem) = updateSkipped(item) { it + Periods.of(item.period) }

    fun undoSkip(item: DueItem) = updateSkipped(item) { it - Periods.of(item.period) }

    private fun updateSkipped(item: DueItem, change: (Set<String>) -> Set<String>) {
        // The newest stored version: the other phone may have changed the template meanwhile.
        val current = templates.value.firstOrNull { it.id == item.template.id } ?: item.template
        val skipped = change(current.skippedPeriods)
        if (skipped == current.skippedPeriods) return
        recurring.update(current.copy(skippedPeriods = skipped, updatedAt = clock.instant()))
    }
}
