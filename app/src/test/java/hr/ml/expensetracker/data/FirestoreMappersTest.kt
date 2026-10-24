package hr.ml.expensetracker.data

import com.google.firebase.Timestamp
import hr.ml.expensetracker.domain.Expense
import hr.ml.expensetracker.domain.Members
import hr.ml.expensetracker.domain.Person
import hr.ml.expensetracker.domain.Settlement
import hr.ml.expensetracker.domain.SplitType
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FirestoreMappersTest {
    private val createdAt = Instant.parse("2026-10-24T08:15:30.123456Z")
    private val updatedAt = Instant.parse("2026-10-24T09:00:00Z")

    private val expense = Expense(
        id = "exp-1",
        date = LocalDate.of(2026, 3, 7),
        category = "Hrana",
        description = "Konzum",
        amountCents = 8_540,
        paidBy = Person.MARINA,
        split = SplitType.FULL_FOR_OTHER,
        createdAt = createdAt,
        updatedAt = updatedAt,
        createdBy = Person.LOVEL,
        deleted = true,
    )

    private val settlement = Settlement(
        id = "set-1",
        date = LocalDate.of(2026, 10, 1),
        from = Person.LOVEL,
        to = Person.MARINA,
        amountCents = 53_470,
        note = "Podmireno",
        createdAt = createdAt,
        updatedAt = updatedAt,
        createdBy = Person.MARINA,
    )

    @Test
    fun expenseDocumentHasTheAgreedLayout() {
        val doc = ExpenseMapper.toDocument(expense)
        assertEquals(
            mapOf(
                "date" to "2026-03-07",
                "category" to "Hrana",
                "description" to "Konzum",
                "amountCents" to 8_540L,
                "paidBy" to "MARINA",
                "split" to "FULL_FOR_OTHER",
                "createdAt" to Timestamp(createdAt.epochSecond, createdAt.nano),
                "updatedAt" to Timestamp(updatedAt.epochSecond, 0),
                "createdBy" to "LOVEL",
                "deleted" to true,
            ),
            doc,
        )
    }

    @Test
    fun expenseRoundTrips() {
        assertEquals(expense, ExpenseMapper.fromDocument("exp-1", ExpenseMapper.toDocument(expense)))
    }

    @Test
    fun settlementDocumentHasTheAgreedLayout() {
        val doc = SettlementMapper.toDocument(settlement)
        assertEquals(
            setOf("date", "from", "to", "amountCents", "note", "createdAt", "updatedAt", "createdBy", "deleted"),
            doc.keys,
        )
        assertEquals("2026-10-01", doc["date"])
        assertEquals("LOVEL", doc["from"])
        assertEquals("MARINA", doc["to"])
        assertEquals(53_470L, doc["amountCents"])
        assertEquals(false, doc["deleted"])
    }

    @Test
    fun settlementRoundTrips() {
        assertEquals(settlement, SettlementMapper.fromDocument("set-1", SettlementMapper.toDocument(settlement)))
    }

    @Test
    fun acceptsIntegerAmountsOfEitherWidth() {
        val doc = ExpenseMapper.toDocument(expense) + ("amountCents" to 8_540)
        assertEquals(8_540L, ExpenseMapper.fromDocument("exp-1", doc).amountCents)
    }

    @Test
    fun rejectsMalformedDocuments() {
        val good = ExpenseMapper.toDocument(expense)
        val broken = listOf(
            good - "category",
            good + ("amountCents" to 85.4),
            good + ("amountCents" to "8540"),
            good + ("date" to "7.3.2026."),
            good + ("date" to Timestamp(0, 0)),
            good + ("paidBy" to "lovel"),
            good + ("split" to "THIRDS"),
            good + ("createdAt" to "2026-10-24"),
            good + ("deleted" to "no"),
        )
        broken.forEach { doc ->
            assertThrows(IllegalArgumentException::class.java) { ExpenseMapper.fromDocument("exp-1", doc) }
        }
    }

    @Test
    fun rejectsSettlementBetweenTheSamePerson() {
        val doc = SettlementMapper.toDocument(settlement) + ("to" to "LOVEL")
        assertThrows(IllegalArgumentException::class.java) { SettlementMapper.fromDocument("set-1", doc) }
    }

    @Test
    fun membersDocumentMapsEmailsToPersons() {
        val members = MembersMapper.fromDocument(
            mapOf(
                "emails" to listOf("Lovel.Test@Example.com", "marina@example.com"),
                "persons" to mapOf(
                    "lovel.test@example.com" to "LOVEL",
                    "marina@example.com" to "MARINA",
                ),
            ),
        )
        assertEquals(
            Members(
                listOf("lovel.test@example.com", "marina@example.com"),
                mapOf("lovel.test@example.com" to Person.LOVEL, "marina@example.com" to Person.MARINA),
            ),
            members,
        )
        assertEquals(Person.LOVEL, members.personFor("LOVEL.TEST@example.com"))
    }

    @Test
    fun membersDocumentIgnoresUnknownPersons() {
        val members = MembersMapper.fromDocument(
            mapOf("emails" to listOf("a@example.com"), "persons" to mapOf("a@example.com" to "ANA")),
        )
        assertEquals(null, members.personFor("a@example.com"))
    }

    @Test
    fun membersDocumentWithoutListsIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { MembersMapper.fromDocument(mapOf("persons" to emptyMap<String, String>())) }
        assertThrows(IllegalArgumentException::class.java) { MembersMapper.fromDocument(mapOf("emails" to emptyList<String>())) }
    }
}
