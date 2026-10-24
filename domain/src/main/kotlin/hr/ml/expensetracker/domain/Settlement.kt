package hr.ml.expensetracker.domain

import java.time.Instant
import java.time.LocalDate

/** An explicit "settle up" payment of [amountCents] from [from] to [to]. */
data class Settlement(
    val id: String,
    val date: LocalDate,
    val from: Person,
    val to: Person,
    val amountCents: Long,
    val note: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val createdBy: Person,
    val deleted: Boolean = false,
) {
    init {
        require(from != to) { "Settlement must be between two different people" }
        require(amountCents > 0) { "amountCents must be positive: $amountCents" }
    }
}
