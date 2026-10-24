package hr.ml.expensetracker.domain

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CategoryNormalizerTest {
    private val normalizer = CategoryNormalizer()

    @ParameterizedTest(name = "''{0}'' -> ''{1}''")
    @CsvSource(
        "hrana, Hrana",
        "HRANA, Hrana",
        "'  Hrana  ', Hrana",
        "hrana VAN, Hrana van",
        "'Hrana   van', Hrana van",
        "Odjeća, Odjeća i oprema",
        "odjeća, Odjeća i oprema",
        "Odjeća i oprema, Odjeća i oprema",
        "režije, Režije",
        "mpo, MPO",
    )
    fun `maps onto the controlled list`(raw: String, expected: String) {
        assertEquals(expected, normalizer.normalize(raw))
    }

    @Test
    fun `unknown categories are trimmed but kept`() {
        assertEquals("Kućni ljubimci", normalizer.normalize("  Kućni ljubimci "))
        assertFalse(normalizer.isKnown("Kućni ljubimci"))
    }

    @Test
    fun `aliases count as known`() {
        assertTrue(normalizer.isKnown("odjeća"))
    }

    @Test
    fun `default list has no duplicates and excludes the alias`() {
        assertEquals(Categories.DEFAULT.size, Categories.DEFAULT.toSet().size)
        assertFalse("Odjeća" in Categories.DEFAULT)
    }
}
