package hr.ml.expensetracker.data

import com.google.firebase.Timestamp
import hr.ml.expensetracker.domain.Expense
import hr.ml.expensetracker.domain.Person
import hr.ml.expensetracker.domain.RecurringTemplate
import hr.ml.expensetracker.domain.SplitType
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** The Stage 10 fields: `templateId`/`period` on expenses and the `recurring/{id}` document. */
class RecurringMappersTest {
    private val createdAt = Instant.parse("2026-10-24T08:15:30.123456Z")
    private val updatedAt = Instant.parse("2026-10-24T09:00:00Z")

    private val posted = Expense(
        id = "rec-t1-2026-10",
        date = LocalDate.of(2026, 10, 15),
        category = "Stanarina",
        description = "",
        amountCents = 50_000,
        paidBy = Person.MARINA,
        split = SplitType.HALF,
        createdAt = createdAt,
        updatedAt = updatedAt,
        createdBy = Person.LOVEL,
        templateId = "t1",
        period = "2026-10",
    )

    private val template = RecurringTemplate(
        id = "t1",
        name = "Stanarina",
        category = "Stanarina",
        description = "Najam stana",
        amountCents = 50_000,
        paidBy = Person.MARINA,
        split = SplitType.HALF,
        dayOfMonth = 15,
        active = true,
        skippedPeriods = setOf("2026-12", "2026-08"),
        createdAt = createdAt,
        updatedAt = updatedAt,
        createdBy = Person.LOVEL,
    )

    @Test
    fun aPostedExpenseCarriesTemplateIdAndPeriod() {
        val doc = ExpenseMapper.toDocument(posted)
        assertEquals("t1", doc["templateId"])
        assertEquals("2026-10", doc["period"])
        assertEquals(12, doc.size)
        assertEquals(posted, ExpenseMapper.fromDocument(posted.id, doc))
    }

    @Test
    fun anOrdinaryExpenseHasNeitherField() {
        val plain = posted.copy(id = "e1", templateId = null, period = null)
        val doc = ExpenseMapper.toDocument(plain)
        assertFalse("templateId" in doc)
        assertFalse("period" in doc)
        val read = ExpenseMapper.fromDocument("e1", doc)
        assertNull(read.templateId)
        assertNull(read.period)
    }

    @Test
    fun templateIdAndPeriodOnlyTogether() {
        val doc = ExpenseMapper.toDocument(posted)
        listOf(
            doc - "period",
            doc - "templateId",
            doc + ("period" to "2026-1"),
            doc + ("period" to 202610L),
            doc + ("templateId" to 5L),
        ).forEach { broken ->
            assertThrows(IllegalArgumentException::class.java) { ExpenseMapper.fromDocument(posted.id, broken) }
        }
    }

    @Test
    fun templateDocumentHasTheAgreedLayout() {
        assertEquals(
            mapOf(
                "name" to "Stanarina",
                "category" to "Stanarina",
                "description" to "Najam stana",
                "amountCents" to 50_000L,
                "paidBy" to "MARINA",
                "split" to "HALF",
                "dayOfMonth" to 15L,
                "active" to true,
                "skippedPeriods" to listOf("2026-08", "2026-12"),
                "createdAt" to Timestamp(createdAt.epochSecond, createdAt.nano),
                "updatedAt" to Timestamp(updatedAt.epochSecond, 0),
                "createdBy" to "LOVEL",
                "deleted" to false,
            ),
            RecurringMapper.toDocument(template),
        )
    }

    @Test
    fun templateRoundTrips() {
        assertEquals(template, RecurringMapper.fromDocument("t1", RecurringMapper.toDocument(template)))
        val inactive = template.copy(active = false, skippedPeriods = emptySet(), deleted = true)
        assertEquals(inactive, RecurringMapper.fromDocument("t1", RecurringMapper.toDocument(inactive)))
    }

    @Test
    fun rejectsMalformedTemplates() {
        val good = RecurringMapper.toDocument(template)
        listOf(
            good - "name",
            good + ("dayOfMonth" to 0L),
            good + ("dayOfMonth" to 32L),
            good + ("dayOfMonth" to 15.5),
            good + ("dayOfMonth" to "15"),
            good + ("active" to "true"),
            good + ("skippedPeriods" to "2026-10"),
            good + ("skippedPeriods" to listOf("2026-10", 5L)),
            good + ("skippedPeriods" to listOf("10/2026")),
            good + ("split" to "THIRDS"),
        ).forEach { doc ->
            assertThrows(IllegalArgumentException::class.java) { RecurringMapper.fromDocument("t1", doc) }
        }
    }
}
