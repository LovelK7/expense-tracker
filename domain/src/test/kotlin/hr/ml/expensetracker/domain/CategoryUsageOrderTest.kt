package hr.ml.expensetracker.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CategoryUsageOrderTest {
    private fun uses(category: String, times: Int, deleted: Boolean = false) =
        List(times) { expense(100, Person.LOVEL, category = category, deleted = deleted) }

    @Test
    fun `no expenses keeps the static picker order`() {
        assertEquals(Categories.FOR_PICKER, Categories.orderByUsage(emptyList()))
    }

    @Test
    fun `most used first, ties and unused categories keep the static order`() {
        val expenses = uses("Putovanja", 3) + uses("Režije", 3) + uses("Hrana", 5) + uses("Zabava", 1)
        val ordered = Categories.orderByUsage(expenses)

        // Režije comes before Putovanja in FOR_PICKER, so it wins the 3:3 tie.
        val used = listOf("Hrana", "Režije", "Putovanja", "Zabava")
        assertEquals(used, ordered.take(4))
        assertEquals(Categories.FOR_PICKER - used.toSet(), ordered.drop(4))
    }

    @Test
    fun `deleted expenses do not count`() {
        val expenses = uses("Zdravlje", 10, deleted = true) + uses("Poklon", 1)
        val ordered = Categories.orderByUsage(expenses)
        assertEquals(listOf("Poklon") + (Categories.FOR_PICKER - "Poklon"), ordered)
    }

    @Test
    fun `only deleted expenses give the static order`() {
        assertEquals(Categories.FOR_PICKER, Categories.orderByUsage(uses("Zabava", 3, deleted = true)))
    }

    @Test
    fun `categories outside the list are not added`() {
        val expenses = uses(Categories.LEGACY_TRANSFER, 50) + uses("Nešto novo", 7) + uses("Usluge", 1)
        val ordered = Categories.orderByUsage(expenses)
        assertFalse(Categories.LEGACY_TRANSFER in ordered)
        assertFalse("Nešto novo" in ordered)
        assertEquals(listOf("Usluge") + (Categories.FOR_PICKER - "Usluge"), ordered)
    }

    @Test
    fun `orders any given list`() {
        val expenses = uses("B", 1) + uses("C", 2)
        assertEquals(listOf("C", "B", "A", "D"), Categories.orderByUsage(expenses, listOf("A", "B", "C", "D")))
    }

    @Test
    fun `legacy frequencies put Putovanja and Rezije before Hrana van`() {
        // Category counts of the legacy POPIS history as of 2026-10-24 (synthetic rows, real counts).
        val expenses = uses("Hrana", 385) + uses("Prijevoz", 173) + uses("Putovanja", 72) + uses("Režije", 70) +
            uses("Hrana van", 60) + uses("MPO", 53) + uses("Potrepštine", 48) + uses("Adaptacija", 45) +
            uses("Stanarina", 37) + uses(Categories.LEGACY_TRANSFER, 32) + uses("Zdravlje", 25)
        assertEquals(
            listOf("Hrana", "Prijevoz", "Putovanja", "Režije", "Hrana van", "MPO", "Potrepštine", "Adaptacija", "Stanarina", "Zdravlje"),
            Categories.orderByUsage(expenses).take(10),
        )
    }
}
