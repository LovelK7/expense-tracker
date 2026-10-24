package hr.ml.expensetracker.domain

import java.time.LocalDate
import java.time.YearMonth

/**
 * Spending summary over an inclusive date range (mirrors the legacy "PIVOT" sheet).
 * All values are in cents and exclude deleted expenses.
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
    /** Number of (non-deleted) expenses in the range. */
    val expenseCount: Int = 0,
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
        fun compute(expenses: Iterable<Expense>, from: LocalDate, to: LocalDate): Statistics {
            require(!to.isBefore(from)) { "Empty range: $from..$to" }
            val inRange = expenses.filter { !it.deleted && !it.date.isBefore(from) && !it.date.isAfter(to) }

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
            )
        }

        fun forMonth(expenses: Iterable<Expense>, month: YearMonth): Statistics =
            compute(expenses, month.atDay(1), month.atEndOfMonth())

        fun forYear(expenses: Iterable<Expense>, year: Int): Statistics =
            compute(expenses, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))

        /**
         * Everything: the range runs from the earliest to the latest non-deleted expense.
         * Null when there is no such expense (there is no range to report on).
         */
        fun forAll(expenses: Iterable<Expense>): Statistics? {
            var from: LocalDate? = null
            var to: LocalDate? = null
            for (e in expenses) {
                if (e.deleted) continue
                if (from == null || e.date.isBefore(from)) from = e.date
                if (to == null || e.date.isAfter(to)) to = e.date
            }
            return if (from == null || to == null) null else compute(expenses, from, to)
        }
    }
}
