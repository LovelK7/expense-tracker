package hr.ml.expensetracker.domain

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecordFilterTest {
    private val t0 = Instant.parse("2026-01-01T00:00:00Z")
    private var seq = 0

    private fun expense(
        category: String = "Hrana",
        description: String = "",
        date: LocalDate = LocalDate.of(2026, 10, 15),
        deleted: Boolean = false,
    ) = Expense("e${++seq}", date, category, description, 100, Person.LOVEL, SplitType.HALF, t0, t0, Person.LOVEL, deleted)

    private fun settlement(note: String = "", date: LocalDate = LocalDate.of(2026, 10, 15), deleted: Boolean = false) =
        Settlement("s${++seq}", date, Person.MARINA, Person.LOVEL, 100, note, t0, t0, Person.MARINA, deleted)

    private fun query(q: String) = RecordFilter.of(query = q)

    @Test
    fun `fold drops case and diacritics`() {
        assertEquals("rezije", SearchText.fold("Režije"))
        assertEquals("cevapcici sljiva dzem", SearchText.fold("ĆEVAPČIĆI Šljiva DŽEM"))
        assertEquals("dakovo", SearchText.fold("Đakovo"))
        assertEquals("odjeca i oprema", SearchText.fold("Odjeća i oprema"))
    }

    @Test
    fun `fold handles precomposed and decomposed input alike`() {
        assertEquals(SearchText.fold("že"), SearchText.fold("že")) // "že" as one code point and as z + caron
    }

    @Test
    fun `terms split on any whitespace and ignore blanks`() {
        assertEquals(listOf("spar", "kastav"), SearchText.terms("  Spar \t KASTAV "))
        assertEquals(emptyList(), SearchText.terms("   "))
        assertFalse(RecordFilter.of(query = "   ").isActive)
    }

    @Test
    fun `query matches the category ignoring case and diacritics`() {
        val rezije = expense(category = "Režije", description = "Struja")
        assertTrue(query("rezije").matches(rezije))
        assertTrue(query("REŽIJE").matches(rezije))
        assertTrue(query("rez").matches(rezije)) // substring
        assertFalse(query("hrana").matches(rezije))
    }

    @Test
    fun `query matches the description`() {
        val spar = expense(category = "Hrana", description = "Spar Kastav")
        assertTrue(query("spar").matches(spar))
        assertTrue(query("kastav").matches(spar))
        assertFalse(query("konzum").matches(spar))
    }

    @Test
    fun `every word must match, in the category or the description`() {
        val spar = expense(category = "Hrana", description = "Spar Kastav")
        assertTrue(query("spar hrana").matches(spar))
        assertTrue(query("kastav spar").matches(spar))
        assertFalse(query("spar konzum").matches(spar))
        // A word is not matched across the category/description boundary.
        assertFalse(query("hranaspar").matches(spar))
    }

    @Test
    fun `settlements match on their note`() {
        val cash = settlement(note = "Gotovina za stan")
        assertTrue(query("gotovina").matches(cash))
        assertFalse(query("hrana").matches(cash))
        assertTrue(RecordFilter.NONE.matches(settlement()))
    }

    @Test
    fun `month filter includes the first and last day and nothing around them`() {
        val october = RecordFilter.of(month = YearMonth.of(2026, 10))
        assertTrue(october.matches(expense(date = LocalDate.of(2026, 10, 1))))
        assertTrue(october.matches(expense(date = LocalDate.of(2026, 10, 31))))
        assertFalse(october.matches(expense(date = LocalDate.of(2026, 9, 30))))
        assertFalse(october.matches(expense(date = LocalDate.of(2026, 11, 1))))
        assertFalse(october.matches(expense(date = LocalDate.of(2025, 10, 15)))) // same month, other year
        assertTrue(october.matches(settlement(date = LocalDate.of(2026, 10, 31))))
        assertFalse(october.matches(settlement(date = LocalDate.of(2026, 11, 1))))
    }

    @Test
    fun `month and query combine`() {
        val filter = RecordFilter.of(YearMonth.of(2026, 10), "spar")
        assertTrue(filter.isActive)
        assertTrue(filter.matches(expense(description = "Spar", date = LocalDate.of(2026, 10, 3))))
        assertFalse(filter.matches(expense(description = "Spar", date = LocalDate.of(2026, 9, 3))))
        assertFalse(filter.matches(expense(description = "Konzum", date = LocalDate.of(2026, 10, 3))))
    }

    @Test
    fun `no filter matches everything except deleted records`() {
        assertFalse(RecordFilter.NONE.isActive)
        assertTrue(RecordFilter.NONE.matches(expense()))
        assertFalse(RecordFilter.NONE.matches(expense(deleted = true)))
        assertFalse(RecordFilter.NONE.matches(settlement(deleted = true)))
    }

    @Test
    fun `months are distinct, newest first, from expenses and settlements`() {
        val months = RecordFilter.months(
            expenses = listOf(
                expense(date = LocalDate.of(2025, 12, 31)),
                expense(date = LocalDate.of(2026, 10, 1)),
                expense(date = LocalDate.of(2026, 10, 31)),
                expense(date = LocalDate.of(2026, 1, 1)),
                expense(date = LocalDate.of(2024, 5, 5), deleted = true),
            ),
            settlements = listOf(
                settlement(date = LocalDate.of(2026, 3, 10)),
                settlement(date = LocalDate.of(2023, 1, 1), deleted = true),
            ),
        )
        assertEquals(
            listOf(YearMonth.of(2026, 10), YearMonth.of(2026, 3), YearMonth.of(2026, 1), YearMonth.of(2025, 12)),
            months,
        )
    }

    @Test
    fun `no records give no months`() {
        assertEquals(emptyList(), RecordFilter.months(emptyList(), emptyList()))
    }
}
