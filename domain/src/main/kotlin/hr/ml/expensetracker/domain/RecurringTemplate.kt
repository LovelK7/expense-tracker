package hr.ml.expensetracker.domain

import java.time.Instant
import java.time.YearMonth
import java.time.format.DateTimeParseException

/**
 * A recurring expense ("Stanarina", 500 € every month on the 15th) that can be posted with one
 * tap. Monthly only: [dayOfMonth] 1..31, clamped to the month's last day in shorter months.
 * [skippedPeriods] holds the months ("yyyy-MM", [Periods]) the user chose not to post.
 */
data class RecurringTemplate(
    val id: String,
    val name: String,
    val category: String,
    val description: String,
    val amountCents: Long,
    val paidBy: Person,
    val split: SplitType,
    val dayOfMonth: Int,
    val active: Boolean,
    val skippedPeriods: Set<String>,
    val createdAt: Instant,
    val updatedAt: Instant,
    val createdBy: Person,
    val deleted: Boolean = false,
) {
    init {
        require(amountCents >= 0) { "amountCents must not be negative: $amountCents" }
        require(dayOfMonth in DAYS) { "dayOfMonth must be 1..31: $dayOfMonth" }
        require(skippedPeriods.all(Periods::isValid)) { "skippedPeriods must be yyyy-MM: $skippedPeriods" }
    }

    fun isSkipped(period: YearMonth): Boolean = Periods.of(period) in skippedPeriods

    companion object {
        val DAYS = 1..31

        /** Matches the limit `firestore.rules` puts on `name`. */
        const val MAX_NAME = 100

        /** The list order: by name (case-insensitive), then oldest first. */
        val BY_NAME: Comparator<RecurringTemplate> =
            compareBy<RecurringTemplate> { it.name.lowercase() }.thenBy { it.createdAt }.thenBy { it.id }
    }
}

/** A month as stored in Firestore: "yyyy-MM" (`YearMonth.toString` for four-digit years). */
object Periods {
    private val FORMAT = Regex("""\d{4}-(0[1-9]|1[0-2])""")

    fun of(month: YearMonth): String = "%04d-%02d".format(month.year, month.monthValue)

    fun isValid(period: String): Boolean = FORMAT.matches(period)

    /** @throws IllegalArgumentException when [period] is not "yyyy-MM". */
    fun parse(period: String): YearMonth {
        require(isValid(period)) { "period must be yyyy-MM: $period" }
        return try {
            YearMonth.parse(period)
        } catch (e: DateTimeParseException) {
            throw IllegalArgumentException("period must be yyyy-MM: $period", e)
        }
    }
}
