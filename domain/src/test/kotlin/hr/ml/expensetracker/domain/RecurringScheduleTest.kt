package hr.ml.expensetracker.domain

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RecurringScheduleTest {
    private val zone = ZoneId.of("Europe/Zagreb")
    private val longAgo = Instant.parse("2025-01-01T00:00:00Z")
    private val october = YearMonth.of(2026, 10)
    private val september = YearMonth.of(2026, 9)

    private fun template(
        id: String = "t1",
        name: String = "Stanarina",
        day: Int = 15,
        active: Boolean = true,
        skipped: Set<String> = emptySet(),
        createdAt: Instant = longAgo,
        deleted: Boolean = false,
    ) = RecurringTemplate(
        id = id,
        name = name,
        category = "Stanarina",
        description = "Najam stana",
        amountCents = 50_000,
        paidBy = Person.MARINA,
        split = SplitType.HALF,
        dayOfMonth = day,
        active = active,
        skippedPeriods = skipped,
        createdAt = createdAt,
        updatedAt = createdAt,
        createdBy = Person.LOVEL,
        deleted = deleted,
    )

    private fun posted(templateId: String, period: String, deleted: Boolean = false) = Expense(
        id = "rec-$templateId-$period",
        date = Periods.parse(period).atDay(15),
        category = "Stanarina",
        description = "",
        amountCents = 50_000,
        paidBy = Person.MARINA,
        split = SplitType.HALF,
        createdAt = longAgo,
        updatedAt = longAgo,
        createdBy = Person.MARINA,
        deleted = deleted,
        templateId = templateId,
        period = period,
    )

    private fun due(
        templates: List<RecurringTemplate>,
        today: LocalDate,
        expenses: List<Expense> = emptyList(),
    ) = RecurringSchedule.due(templates, expenses, today, zone)

    @Test
    fun dueFromTheDayOfMonthOn() {
        val t = template(day = 15, skipped = setOf("2026-09"))
        assertEquals(emptyList(), due(listOf(t), LocalDate.of(2026, 10, 14)))
        assertEquals(listOf(DueItem(t, october, overdue = false)), due(listOf(t), LocalDate.of(2026, 10, 15)))
        assertEquals(listOf(DueItem(t, october, overdue = false)), due(listOf(t), LocalDate.of(2026, 10, 31)))
    }

    @Test
    fun theUnpostedPreviousMonthIsOverdue() {
        val t = template(day = 15)
        // Before this month's day: only last month, overdue.
        assertEquals(listOf(DueItem(t, september, overdue = true)), due(listOf(t), LocalDate.of(2026, 10, 3)))
        // From the day on: both, oldest first.
        assertEquals(
            listOf(DueItem(t, september, overdue = true), DueItem(t, october, overdue = false)),
            due(listOf(t), LocalDate.of(2026, 10, 24)),
        )
    }

    @Test
    fun atMostTwoPeriods() {
        val t = template(day = 1)
        val items = due(listOf(t), LocalDate.of(2026, 10, 24))
        assertEquals(listOf(september, october), items.map { it.period })
    }

    @Test
    fun skippedPeriodsAreNotDue() {
        val t = template(skipped = setOf("2026-09", "2026-10"))
        assertEquals(emptyList(), due(listOf(t), LocalDate.of(2026, 10, 24)))
        val onlyOctober = template(skipped = setOf("2026-10"))
        assertEquals(listOf(september), due(listOf(onlyOctober), LocalDate.of(2026, 10, 24)).map { it.period })
    }

    @Test
    fun alreadyPostedPeriodsAreNotDue() {
        val t = template()
        val today = LocalDate.of(2026, 10, 24)
        assertEquals(listOf(october), due(listOf(t), today, listOf(posted("t1", "2026-09"))).map { it.period })
        assertEquals(emptyList(), due(listOf(t), today, listOf(posted("t1", "2026-09"), posted("t1", "2026-10"))))
        // Another template's expense or a deleted posting does not count.
        assertEquals(
            listOf(september, october),
            due(listOf(t), today, listOf(posted("t2", "2026-09"), posted("t1", "2026-10", deleted = true))).map { it.period },
        )
    }

    @Test
    fun aManualExpenseInTheSameCategoryDoesNotCount() {
        val manual = posted("t1", "2026-10").copy(id = "x", templateId = null, period = null)
        assertEquals(2, due(listOf(template()), LocalDate.of(2026, 10, 24), listOf(manual)).size)
    }

    @Test
    fun inactiveAndDeletedTemplatesAreNeverDue() {
        val today = LocalDate.of(2026, 10, 24)
        assertEquals(emptyList(), due(listOf(template(active = false), template(id = "t2", deleted = true)), today))
    }

    @Test
    fun monthsBeforeTheTemplateWasCreatedAreNotDue() {
        // Created on 24 October (Zagreb time) for the 15th: October is due, September is not.
        val t = template(createdAt = Instant.parse("2026-10-24T08:00:00Z"))
        assertEquals(listOf(DueItem(t, october, overdue = false)), due(listOf(t), LocalDate.of(2026, 10, 24)))
        // Created at 00:30 on 1 October in Zagreb (still 30 September in UTC): September is not due.
        val early = template(createdAt = Instant.parse("2026-09-30T22:30:00Z"))
        assertEquals(listOf(october), due(listOf(early), LocalDate.of(2026, 10, 24)).map { it.period })
    }

    @Test
    fun shortMonthsClampTheDay() {
        val t = template(day = 31)
        assertEquals(LocalDate.of(2026, 2, 28), RecurringSchedule.dueDate(t, YearMonth.of(2026, 2)))
        assertEquals(LocalDate.of(2028, 2, 29), RecurringSchedule.dueDate(t, YearMonth.of(2028, 2)))
        assertEquals(LocalDate.of(2026, 4, 30), RecurringSchedule.dueDate(t, YearMonth.of(2026, 4)))
        assertEquals(LocalDate.of(2026, 10, 31), RecurringSchedule.dueDate(t, october))
        // Due on the last day of February, not only from a "31st" that never comes.
        val feb = due(listOf(template(day = 31, skipped = setOf("2026-01"))), LocalDate.of(2026, 2, 28))
        assertEquals(listOf(DueItem(feb.single().template, YearMonth.of(2026, 2), overdue = false)), feb)
        assertEquals(LocalDate.of(2026, 2, 28), feb.single().date)
        assertEquals(emptyList(), due(listOf(template(day = 31, skipped = setOf("2026-01"))), LocalDate.of(2026, 2, 27)))
        // 30 April with the day 31: due on the 30th.
        assertEquals(1, due(listOf(template(day = 31, skipped = setOf("2026-03"))), LocalDate.of(2026, 4, 30)).size)
    }

    @Test
    fun theYearBoundary() {
        val t = template(day = 10)
        val items = due(listOf(t), LocalDate.of(2027, 1, 12))
        assertEquals(listOf(YearMonth.of(2026, 12), YearMonth.of(2027, 1)), items.map { it.period })
        assertTrue(items[0].overdue)
        assertFalse(items[1].overdue)
    }

    @Test
    fun orderedByPeriodThenDateThenName() {
        val a = template(id = "a", name = "Vrtić", day = 5)
        val b = template(id = "b", name = "Internet", day = 5)
        val c = template(id = "c", name = "Stanarina", day = 1)
        val items = due(listOf(a, b, c), LocalDate.of(2026, 10, 24)).map { it.template.id to it.period }
        assertEquals(
            listOf("c" to september, "b" to september, "a" to september, "c" to october, "b" to october, "a" to october),
            items,
        )
    }

    @Test
    fun deterministicExpenseIds() {
        assertEquals("rec-abc123-2026-10", RecurringSchedule.expenseId("abc123", october))
        assertEquals("rec-abc123-2027-01", RecurringSchedule.expenseId("abc123", YearMonth.of(2027, 1)))
        assertEquals("rec-t1-2026-09", DueItem(template(), september, overdue = true).expenseId)
    }

    @Test
    fun thePostedExpenseCarriesTheTemplate() {
        val now = Instant.parse("2026-10-24T10:00:00Z")
        val item = DueItem(template(day = 31), YearMonth.of(2026, 2), overdue = false)
        val e = RecurringSchedule.expenseFor(item, Person.LOVEL, now)
        assertEquals(
            Expense(
                id = "rec-t1-2026-02",
                date = LocalDate.of(2026, 2, 28),
                category = "Stanarina",
                description = "Najam stana",
                amountCents = 50_000,
                paidBy = Person.MARINA,
                split = SplitType.HALF,
                createdAt = now,
                updatedAt = now,
                createdBy = Person.LOVEL,
                templateId = "t1",
                period = "2026-02",
            ),
            e,
        )
    }

    @Test
    fun periodsAreYearDashMonth() {
        assertEquals("2026-10", Periods.of(october))
        assertEquals("0999-01", Periods.of(YearMonth.of(999, 1)))
        assertEquals(october, Periods.parse("2026-10"))
        assertFalse(Periods.isValid("2026-13"))
        assertFalse(Periods.isValid("2026-00"))
        assertFalse(Periods.isValid("2026-1"))
        assertFalse(Periods.isValid("2026-10-01"))
        assertThrows<IllegalArgumentException> { Periods.parse("10/2026") }
    }

    @Test
    fun modelsRejectBadValues() {
        assertThrows<IllegalArgumentException> { template(day = 0) }
        assertThrows<IllegalArgumentException> { template(day = 32) }
        assertThrows<IllegalArgumentException> { template(skipped = setOf("2026-1")) }
        assertThrows<IllegalArgumentException> { posted("t1", "2026-10").copy(period = null) }
        assertThrows<IllegalArgumentException> { posted("t1", "2026-10").copy(templateId = null) }
        assertThrows<IllegalArgumentException> { posted("t1", "2026-10").copy(period = "2026/10") }
    }
}
