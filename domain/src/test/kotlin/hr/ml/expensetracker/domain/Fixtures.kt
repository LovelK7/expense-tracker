package hr.ml.expensetracker.domain

import java.time.Instant
import java.time.LocalDate

private val T0: Instant = Instant.parse("2026-01-01T00:00:00Z")
private var seq = 0

fun expense(
    cents: Long,
    paidBy: Person,
    split: SplitType = SplitType.HALF,
    date: LocalDate = LocalDate.of(2026, 1, 15),
    category: String = "Hrana",
    deleted: Boolean = false,
) = Expense(
    id = "e${++seq}",
    date = date,
    category = category,
    description = "",
    amountCents = cents,
    paidBy = paidBy,
    split = split,
    createdAt = T0,
    updatedAt = T0,
    createdBy = paidBy,
    deleted = deleted,
)

fun settlement(cents: Long, from: Person, to: Person = from.other, deleted: Boolean = false) = Settlement(
    id = "s${++seq}",
    date = LocalDate.of(2026, 1, 20),
    from = from,
    to = to,
    amountCents = cents,
    note = "",
    createdAt = T0,
    updatedAt = T0,
    createdBy = from,
    deleted = deleted,
)
