package hr.ml.expensetracker.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class CroatianPluralTest {
    private fun troskova(n: Long) = "$n " + CroatianPlural.choose(n, "trošak", "troška", "troškova")

    @Test
    fun `one after 1, 21, 101 but not 11`() {
        assertEquals("1 trošak", troskova(1))
        assertEquals("21 trošak", troskova(21))
        assertEquals("101 trošak", troskova(101))
        assertEquals("1001 trošak", troskova(1001))
        assertEquals("11 troškova", troskova(11))
        assertEquals("111 troškova", troskova(111))
    }

    @Test
    fun `few after 2 to 4 but not the teens`() {
        for (n in listOf(2L, 3L, 4L, 22L, 23L, 104L, 1_062L)) assertEquals("$n troška", troskova(n))
        for (n in listOf(12L, 13L, 14L, 112L, 214L)) assertEquals("$n troškova", troskova(n))
    }

    @Test
    fun `many after 0 and 5 to 20`() {
        for (n in listOf(0L, 5L, 9L, 10L, 15L, 20L, 25L, 100L, 1_069L)) assertEquals("$n troškova", troskova(n))
    }

    @Test
    fun `negative counts use the same form`() {
        assertEquals("troška", CroatianPlural.choose(-3, "trošak", "troška", "troškova"))
    }
}
