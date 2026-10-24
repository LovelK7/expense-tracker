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

    /**
     * Stores an expense under a deterministic id (a posted [RecurringTemplate],
     * [RecurringSchedule.expenseId]). When a document with that id already exists (posted and
     * deleted before, or posted meanwhile on the other phone) it is overwritten but keeps its
     * stored `createdBy` and `createdAt`, which the security rules never let change.
     */
    fun addOrRestore(expense: Expense) = add(expense)

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

/**
 * Storage for [RecurringTemplate] records; same offline-first contract as [ExpenseRepository]
 * (Firestore collection `recurring`).
 */
interface RecurringRepository {
    /** Every non-deleted template, by name. */
    fun observeAll(): Flow<List<RecurringTemplate>>

    fun newId(): String

    fun add(template: RecurringTemplate)

    /** Replaces an existing template. [RecurringTemplate.createdBy] and `createdAt` must not change. */
    fun update(template: RecurringTemplate)

    /** Marks the template deleted and bumps its `updatedAt`. */
    fun softDelete(id: String)
}
