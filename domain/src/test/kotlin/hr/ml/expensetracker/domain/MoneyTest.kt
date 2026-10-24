package hr.ml.expensetracker.domain

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals

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
}
