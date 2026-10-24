package hr.ml.expensetracker.domain

import java.time.LocalDate
import java.time.YearMonth

/**
 * Spending summary over an inclusive date range (mirrors the legacy "PIVOT" sheet).
 * All values are in cents and exclude deleted expenses.
 *
 * Transfers ([Categories.TRANSFER], money moved between the two people) are not spending: they
 * are left out of every figure here except [transferCents]. With a [person] every figure covers
 * only the expenses that person paid (see docs/DESIGN.md, "Statistics rules").
 */
data class Statistics(
    val from: LocalDate,
    val to: LocalDate,
    val totalCents: Long,
    /** Gross amount per category, largest first. */
    val byCategory: Map<String, Long>,
    /** Gross amount per calendar month, chronological; months without expenses are omitted. */
    val byMonth: Map<YearMonth, Long>,
    /**
     * What each person consumed: half of every HALF expense plus the full amount of every
     * FULL_FOR_OTHER expense paid on their behalf. The two values always add up to [totalCents];
     * an odd HALF total gives Lovel the half-even rounded half and Marina the remainder.
     */
    val shareByPerson: Map<Person, Long>,
    /** How much each person actually paid. */
    val paidByPerson: Map<Person, Long>,
    /** Number of (non-deleted, non-transfer) expenses in the range. */
    val expenseCount: Int = 0,
    /** The transfers in the range (paid by [person], if set), excluded from everything above. */
    val transferCents: Long = 0,
    val transferCount: Int = 0,
    /** Only expenses paid by this person are counted; null for both. */
    val person: Person? = null,
) {
    /**
     * [byMonth] with the gaps filled: every calendar month from the first to the last month that
     * has expenses, chronological, zero for months without any. Empty when there are no expenses.
     */
    fun byMonthContinuous(): Map<YearMonth, Long> {
        if (byMonth.isEmpty()) return emptyMap()
        val first = byMonth.keys.first()
        val last = byMonth.keys.last()
        return generateSequence(first) { it.plusMonths(1) }
            .takeWhile { !it.isAfter(last) }
            .associateWith { byMonth[it] ?: 0L }
    }

    companion object {
        /** A live expense that counts as spending: not a transfer, paid by [person] when set. */
        fun isSpending(e: Expense, person: Person? = null): Boolean =
            !e.deleted && e.category != Categories.TRANSFER && (person == null || e.paidBy == person)

        fun compute(expenses: Iterable<Expense>, from: LocalDate, to: LocalDate, person: Person? = null): Statistics {
            require(!to.isBefore(from)) { "Empty range: $from..$to" }
            val inRange = ArrayList<Expense>()
            var transferCents = 0L
            var transferCount = 0
            for (e in expenses) {
                if (e.deleted || e.date.isBefore(from) || e.date.isAfter(to)) continue
                if (person != null && e.paidBy != person) continue
                if (e.category == Categories.TRANSFER) {
                    transferCents += e.amountCents
                    transferCount++
                } else {
                    inRange += e
                }
            }

            val byCategory = inRange.groupingBy { it.category }
                .fold(0L) { acc, e -> acc + e.amountCents }
                .entries.sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
                .associate { it.key to it.value }

            val byMonth = inRange.groupingBy { YearMonth.from(it.date) }
                .fold(0L) { acc, e -> acc + e.amountCents }
                .toSortedMap()

            val halfTotal = inRange.filter { it.split == SplitType.HALF }.sumOf { it.amountCents }
            val lovelHalf = halveHalfEven(halfTotal)
            fun fullFor(p: Person) =
                inRange.filter { it.split == SplitType.FULL_FOR_OTHER && it.paidBy.other == p }.sumOf { it.amountCents }

            return Statistics(
                from = from,
                to = to,
                totalCents = inRange.sumOf { it.amountCents },
                byCategory = byCategory,
                byMonth = byMonth,
                shareByPerson = mapOf(
                    Person.LOVEL to lovelHalf + fullFor(Person.LOVEL),
                    Person.MARINA to (halfTotal - lovelHalf) + fullFor(Person.MARINA),
                ),
                paidByPerson = Person.entries.associateWith { p ->
                    inRange.filter { it.paidBy == p }.sumOf { it.amountCents }
                },
                expenseCount = inRange.size,
                transferCents = transferCents,
                transferCount = transferCount,
                person = person,
            )
        }

        fun forMonth(expenses: Iterable<Expense>, month: YearMonth, person: Person? = null): Statistics =
            compute(expenses, month.atDay(1), month.atEndOfMonth(), person)

        fun forYear(expenses: Iterable<Expense>, year: Int, person: Person? = null): Statistics =
            compute(expenses, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31), person)

        /**
         * Everything: the range runs from the earliest to the latest non-deleted expense of either
         * person, transfers included, so it is the same whoever [person] is. Null when there is no
         * such expense (there is no range to report on).
         */
        fun forAll(expenses: Iterable<Expense>, person: Person? = null): Statistics? {
            val range = liveRange(expenses) ?: return null
            return compute(expenses, range.start, range.endInclusive, person)
        }

        /** The earliest to the latest date of a non-deleted expense; null when there is none. */
        fun liveRange(expenses: Iterable<Expense>): ClosedRange<LocalDate>? {
            var from: LocalDate? = null
            var to: LocalDate? = null
            for (e in expenses) {
                if (e.deleted) continue
                if (from == null || e.date.isBefore(from)) from = e.date
                if (to == null || e.date.isAfter(to)) to = e.date
            }
            return if (from == null || to == null) null else from..to
        }
    }
}
