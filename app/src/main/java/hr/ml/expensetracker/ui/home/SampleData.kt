package hr.ml.expensetracker.ui.home

import hr.ml.expensetracker.domain.Expense
import hr.ml.expensetracker.domain.Person
import hr.ml.expensetracker.domain.Settlement
import hr.ml.expensetracker.domain.SplitType
import java.time.Instant
import java.time.LocalDate

/** Hardcoded placeholder data until the Firestore repository exists (Stage 2). */
object SampleData {
    private val created = Instant.parse("2026-10-01T12:00:00Z")

    private fun expense(
        id: String,
        day: Int,
        category: String,
        description: String,
        cents: Long,
        paidBy: Person,
        split: SplitType,
    ) = Expense(
        id = id,
        date = LocalDate.of(2026, 10, day),
        category = category,
        description = description,
        amountCents = cents,
        paidBy = paidBy,
        split = split,
        createdAt = created,
        updatedAt = created,
        createdBy = paidBy,
    )

    val expenses: List<Expense> = listOf(
        expense("sample-1", 1, "Stanarina", "Stanarina listopad", 100_000, Person.LOVEL, SplitType.HALF),
        expense("sample-2", 3, "Hrana", "Konzum", 8_540, Person.MARINA, SplitType.HALF),
        expense("sample-3", 5, "Hrana", "Spar", 6_200, Person.LOVEL, SplitType.HALF),
        expense("sample-4", 9, "Poklon", "Poklon za mamu", 2_500, Person.MARINA, SplitType.FULL_FOR_OTHER),
        expense("sample-5", 12, "Zdravlje", "Ljekarna", 7_140, Person.LOVEL, SplitType.FULL_FOR_OTHER),
    )

    val settlements: List<Settlement> = emptyList()
}
