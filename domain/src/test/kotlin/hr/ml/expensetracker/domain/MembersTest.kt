package hr.ml.expensetracker.domain

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class MembersTest {
    private val members = Members(
        emails = listOf("Lovel.Test@Example.com", " marina@example.com "),
        persons = mapOf("lovel.test@example.com" to Person.LOVEL, "MARINA@example.com" to Person.MARINA),
    )

    @Test
    fun resolvesEachMemberCaseInsensitively() {
        assertEquals(Person.LOVEL, members.personFor("lovel.test@example.com"))
        assertEquals(Person.LOVEL, members.personFor("LOVEL.TEST@EXAMPLE.COM"))
        assertEquals(Person.MARINA, members.personFor(" Marina@Example.com"))
    }

    @Test
    fun unknownOrMissingEmailIsNotAMember() {
        assertNull(members.personFor("someone@example.com"))
        assertNull(members.personFor(null))
        assertNull(members.personFor(""))
    }

    @Test
    fun emailWithPersonButNotInTheAllowListIsNotAMember() {
        val m = Members(emails = listOf("a@example.com"), persons = mapOf("b@example.com" to Person.MARINA))
        assertNull(m.personFor("b@example.com"))
    }

    @Test
    fun allowListedEmailWithoutPersonIsNotAMember() {
        val m = Members(emails = listOf("a@example.com"), persons = emptyMap())
        assertNull(m.personFor("a@example.com"))
    }

    @Test
    fun storesNormalizedEmails() {
        assertEquals(setOf("lovel.test@example.com", "marina@example.com"), members.emails)
        assertEquals(setOf("lovel.test@example.com", "marina@example.com"), members.persons.keys)
    }
}
