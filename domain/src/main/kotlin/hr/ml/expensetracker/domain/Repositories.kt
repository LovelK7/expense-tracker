package hr.ml.expensetracker.domain

import kotlinx.coroutines.flow.Flow

/**
 * Storage for [Expense] records. Implementations are offline-first: a write lands in the local
 * cache at once (observers see it immediately) and syncs later, so the write functions return
 * without waiting for the server. Records are never removed; [softDelete] sets [Expense.deleted].
 */
interface ExpenseRepository {
    /** Every non-deleted expense, newest first; emits again on every local or remote change. */
    fun observeAll(): Flow<List<Expense>>

    /** A fresh client-generated id for a new record. */
    fun newId(): String

    /** Stores a new expense under [Expense.id] (take it from [newId]). */
    fun add(expense: Expense)

    /** Replaces an existing expense. [Expense.createdBy] and [Expense.createdAt] must not change. */
    fun update(expense: Expense)

    /** Marks the expense deleted and bumps its `updatedAt`. */
    fun softDelete(id: String)
}

/** Storage for [Settlement] records; same contract as [ExpenseRepository]. */
interface SettlementRepository {
    /** Every non-deleted settlement, newest first. */
    fun observeAll(): Flow<List<Settlement>>

    fun newId(): String

    fun add(settlement: Settlement)

    fun update(settlement: Settlement)

    fun softDelete(id: String)
}
