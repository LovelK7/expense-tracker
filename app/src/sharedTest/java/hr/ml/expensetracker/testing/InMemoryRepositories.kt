package hr.ml.expensetracker.testing

import hr.ml.expensetracker.domain.Expense
import hr.ml.expensetracker.domain.ExpenseRepository
import hr.ml.expensetracker.domain.RecurringRepository
import hr.ml.expensetracker.domain.RecurringTemplate
import hr.ml.expensetracker.domain.Settlement
import hr.ml.expensetracker.domain.SettlementRepository
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Repository fakes with the Firestore implementations' contract: writes show up in
 * [observeAll] at once, deleted records are hidden, nothing is ever removed.
 */
class InMemoryExpenseRepository(
    initial: List<Expense> = emptyList(),
    private val clock: () -> Instant = Instant::now,
) : ExpenseRepository {
    /** Every stored record by id, deleted ones included. */
    val records = MutableStateFlow(initial.associateBy { it.id })
    private var nextId = 0

    override fun observeAll(): Flow<List<Expense>> = records.map { all ->
        all.values.filterNot { it.deleted }
            .sortedWith(compareByDescending<Expense> { it.date }.thenByDescending { it.createdAt })
    }

    override fun newId() = "expense-${++nextId}"

    override fun add(expense: Expense) = records.update { it + (expense.id to expense) }

    override fun update(expense: Expense) = records.update { it + (expense.id to expense) }

    override fun addOrRestore(expense: Expense) = records.update { all ->
        val stored = all[expense.id]
        val kept = if (stored == null) expense else expense.copy(createdBy = stored.createdBy, createdAt = stored.createdAt)
        all + (expense.id to kept)
    }

    override fun softDelete(id: String) = records.update { all ->
        val e = all[id] ?: return@update all
        all + (id to e.copy(deleted = true, updatedAt = clock()))
    }
}

class InMemorySettlementRepository(
    initial: List<Settlement> = emptyList(),
    private val clock: () -> Instant = Instant::now,
) : SettlementRepository {
    val records = MutableStateFlow(initial.associateBy { it.id })
    private var nextId = 0

    override fun observeAll(): Flow<List<Settlement>> = records.map { all ->
        all.values.filterNot { it.deleted }
            .sortedWith(compareByDescending<Settlement> { it.date }.thenByDescending { it.createdAt })
    }

    override fun newId() = "settlement-${++nextId}"

    override fun add(settlement: Settlement) = records.update { it + (settlement.id to settlement) }

    override fun update(settlement: Settlement) = records.update { it + (settlement.id to settlement) }

    override fun softDelete(id: String) = records.update { all ->
        val s = all[id] ?: return@update all
        all + (id to s.copy(deleted = true, updatedAt = clock()))
    }
}

class InMemoryRecurringRepository(
    initial: List<RecurringTemplate> = emptyList(),
    private val clock: () -> Instant = Instant::now,
) : RecurringRepository {
    val records = MutableStateFlow(initial.associateBy { it.id })
    private var nextId = 0

    override fun observeAll(): Flow<List<RecurringTemplate>> = records.map { all ->
        all.values.filterNot { it.deleted }.sortedWith(RecurringTemplate.BY_NAME)
    }

    override fun newId() = "template-${++nextId}"

    override fun add(template: RecurringTemplate) = records.update { it + (template.id to template) }

    override fun update(template: RecurringTemplate) = records.update { it + (template.id to template) }

    override fun softDelete(id: String) = records.update { all ->
        val t = all[id] ?: return@update all
        all + (id to t.copy(deleted = true, updatedAt = clock()))
    }
}
