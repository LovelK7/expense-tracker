package hr.ml.expensetracker.domain

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** [template] is due for [period]; [overdue] when that is the previous month ("zaostalo"). */
data class DueItem(val template: RecurringTemplate, val period: YearMonth, val overdue: Boolean) {
    /** The posted expense's date: [RecurringTemplate.dayOfMonth] clamped to the month's length. */
    val date: LocalDate get() = RecurringSchedule.dueDate(template, period)

    /** The deterministic id of the expense posted for this item. */
    val expenseId: String get() = RecurringSchedule.expenseId(template.id, period)

    /** Unique per template and month (a list key). */
    val key: String get() = expenseId
}

/**
 * When a [RecurringTemplate] is due, and the expense it posts. Pure; the "Dospjelo" cards on
 * Početna show [due].
 */
object RecurringSchedule {
    /** The id of the expense posted from [templateId] for [period]: "rec-<templateId>-<yyyy-MM>". */
    fun expenseId(templateId: String, period: YearMonth): String = "rec-$templateId-${Periods.of(period)}"

    /** [RecurringTemplate.dayOfMonth] in [period], clamped to its last day (31 → 28/29 February). */
    fun dueDate(template: RecurringTemplate, period: YearMonth): LocalDate =
        period.atDay(minOf(template.dayOfMonth, period.lengthOfMonth()))

    /**
     * Every due (template, period) pair on [today]: per template at most the current month and
     * the previous one (overdue). A period is due when its (clamped) due date is not after
     * [today], it is not in [RecurringTemplate.skippedPeriods], no non-deleted expense of
     * [expenses] carries this template and period, and it is not before the month the template
     * was created in (in [zone]), so a new template does not report last month as overdue.
     * Inactive and deleted templates are never due. Oldest period first, then by due date and name.
     */
    fun due(
        templates: List<RecurringTemplate>,
        expenses: List<Expense>,
        today: LocalDate,
        zone: ZoneId,
    ): List<DueItem> {
        val posted = expenses.asSequence()
            .filter { !it.deleted && it.templateId != null }
            .map { it.templateId to it.period }
            .toSet()
        val current = YearMonth.from(today)
        val periods = listOf(current.minusMonths(1) to true, current to false)
        return templates.asSequence()
            .filter { it.active && !it.deleted }
            .flatMap { t ->
                val created = YearMonth.from(t.createdAt.atZone(zone))
                periods.asSequence()
                    .filter { (period, _) ->
                        period >= created &&
                            !dueDate(t, period).isAfter(today) &&
                            !t.isSkipped(period) &&
                            (t.id to Periods.of(period)) !in posted
                    }
                    .map { (period, overdue) -> DueItem(t, period, overdue) }
            }
            .sortedWith(
                compareBy<DueItem> { it.period }
                    .thenBy { it.date }
                    .thenBy { it.template.name.lowercase() }
                    .thenBy { it.template.id },
            )
            .toList()
    }

    /**
     * The expense [item] posts: the template's fields on [DueItem.date], with the deterministic
     * [DueItem.expenseId] so both phones posting the same month write the same document.
     */
    fun expenseFor(item: DueItem, createdBy: Person, now: Instant): Expense {
        val t = item.template
        return Expense(
            id = item.expenseId,
            date = item.date,
            category = t.category,
            description = t.description,
            amountCents = t.amountCents,
            paidBy = t.paidBy,
            split = t.split,
            createdAt = now,
            updatedAt = now,
            createdBy = createdBy,
            templateId = t.id,
            period = Periods.of(item.period),
        )
    }
}
