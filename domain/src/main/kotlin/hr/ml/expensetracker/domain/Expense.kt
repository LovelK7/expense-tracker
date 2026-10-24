package hr.ml.expensetracker.domain

import java.time.Instant
import java.time.LocalDate

data class Expense(
    val id: String,
    val date: LocalDate,
    val category: String,
    val description: String,
    val amountCents: Long,
    val paidBy: Person,
    val split: SplitType,
    val createdAt: Instant,
    val updatedAt: Instant,
    val createdBy: Person,
    val deleted: Boolean = false,
) {
    init {
        require(amountCents >= 0) { "amountCents must not be negative: $amountCents" }
    }
}
