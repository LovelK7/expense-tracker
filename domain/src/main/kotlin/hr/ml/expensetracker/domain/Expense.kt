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
    /** The [RecurringTemplate] this expense was posted from (a "Dospjelo" card); null otherwise. */
    val templateId: String? = null,
    /** The month ("yyyy-MM", [Periods]) the template was posted for; set exactly when [templateId] is. */
    val period: String? = null,
) {
    init {
        require(amountCents >= 0) { "amountCents must not be negative: $amountCents" }
        require((templateId == null) == (period == null)) { "templateId and period go together" }
        require(period == null || Periods.isValid(period)) { "period must be yyyy-MM: $period" }
    }
}
