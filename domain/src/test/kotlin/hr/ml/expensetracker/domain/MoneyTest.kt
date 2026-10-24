package hr.ml.expensetracker.domain

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MoneyTest {
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(
        "53470, '534,70 €'",
        "-53470, '-534,70 €'",
        "0, '0,00 €'",
        "5, '0,05 €'",
        "-5, '-0,05 €'",
        "100, '1,00 €'",
        "123456, '1.234,56 €'",
        "123456789, '1.234.567,89 €'",
    )
    fun `formats cents in Croatian style`(cents: Long, expected: String) {
        assertEquals(expected, Money.format(cents).replace(Money.NBSP, ' '))
    }

    @org.junit.jupiter.api.Test
    fun `uses a non-breaking space before the euro sign`() {
        assertEquals("534,70\u00A0€", Money.format(53470))
    }

    @ParameterizedTest(name = "''{0}'' -> {1}")
    @CsvSource(
        "'12,5', 1250",
        "'12.50', 1250",
        "'12', 1200",
        "'12,', 1200",
        "',5', 50",
        "'0,05', 5",
        "' 7,99 ', 799",
        "'0', 0",
        "'1234,56', 123456",
    )
    fun `parses typed amounts with comma or dot`(input: String, cents: Long) {
        assertEquals(cents, Money.parseCents(input))
    }

    @ParameterizedTest(name = "''{0}'' is rejected")
    @CsvSource("''", "','", "'12,345'", "'1.234,56'", "'-5'", "'abc'", "'12 €'", "'1234567890'")
    fun `rejects input that is not a plain amount`(input: String) {
        assertNull(Money.parseCents(input))
    }

    @org.junit.jupiter.api.Test
    fun `formats cents for an input field`() {
        assertEquals("12,50", Money.formatInput(1250))
        assertEquals("534,70", Money.formatInput(53470))
        assertEquals("0,05", Money.formatInput(5))
        assertEquals(1250L, Money.parseCents(Money.formatInput(1250)))
    }
}
