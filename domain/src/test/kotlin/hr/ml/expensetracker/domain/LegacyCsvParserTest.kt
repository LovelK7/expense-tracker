package hr.ml.expensetracker.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertContains
import kotlin.test.assertEquals

class LegacyCsvParserTest {
    private val csv = """
        date,category,description,amount_cents,paid_by,split
        2023-01-02,Putovanja,Putovanje po Bosni,17880,LOVEL,HALF
        2023-01-03,hrana,"Ribe, blitva i ""friška"" riba",1023,MARINA,FULL_FOR_OTHER
        2023-01-04,Odjeća,"Dvije
        linije",0,LOVEL,HALF
    """.trimIndent() + "\n"

    @Test
    fun `parses rows into expenses`() {
        val rows = LegacyCsvParser.parse(csv)
        assertEquals(3, rows.size)
        val first = rows[0]
        assertEquals("legacy-0001", first.id)
        assertEquals(LocalDate.of(2023, 1, 2), first.date)
        assertEquals("Putovanja", first.category)
        assertEquals("Putovanje po Bosni", first.description)
        assertEquals(17880, first.amountCents)
        assertEquals(Person.LOVEL, first.paidBy)
        assertEquals(SplitType.HALF, first.split)
        assertEquals(Person.LOVEL, first.createdBy)
        assertEquals(Instant.parse("2023-01-02T00:00:00Z"), first.createdAt)
        assertEquals(false, first.deleted)
        // Legacy rows were never posted from a recurring template.
        assertEquals(listOf(null), rows.map { it.templateId to it.period }.map { (t, p) -> t ?: p }.distinct())
    }

    @Test
    fun `handles quoted commas, escaped quotes and newlines`() {
        val rows = LegacyCsvParser.parse(csv)
        assertEquals("Ribe, blitva i \"friška\" riba", rows[1].description)
        assertEquals(SplitType.FULL_FOR_OTHER, rows[1].split)
        assertEquals("Dvije\nlinije", rows[2].description)
        assertEquals(0, rows[2].amountCents)
    }

    @Test
    fun `keeps raw categories unless a normalizer is given`() {
        assertEquals(listOf("Putovanja", "hrana", "Odjeća"), LegacyCsvParser.parse(csv).map { it.category })
        assertEquals(
            listOf("Putovanja", "Hrana", "Odjeća i oprema"),
            LegacyCsvParser.parse(csv, CategoryNormalizer()).map { it.category },
        )
    }

    @Test
    fun `accepts CRLF line endings and a BOM`() {
        val rows = LegacyCsvParser.parse("\uFEFF" + csv.replace("\n", "\r\n"))
        assertEquals(3, rows.size)
        assertEquals("Dvije\nlinije", rows[2].description)
    }

    @Test
    fun `rejects a wrong header`() {
        assertThrows<IllegalArgumentException> { LegacyCsvParser.parse("date,amount\n2023-01-01,5\n") }
    }

    @Test
    fun `reports the line of a bad row`() {
        val bad = "date,category,description,amount_cents,paid_by,split\n2023-01-02,Hrana,x,12,LOVEL,HALF\n2023-01-03,Hrana,x,12,PERO,HALF\n"
        val e = assertThrows<IllegalArgumentException> { LegacyCsvParser.parse(bad) }
        assertContains(e.message!!, "Line 3")
        assertContains(e.message!!, "paid_by")
    }

    @Test
    fun `rejects a bad date`() {
        val bad = "date,category,description,amount_cents,paid_by,split\n02.01.2023,Hrana,x,12,LOVEL,HALF\n"
        val e = assertThrows<IllegalArgumentException> { LegacyCsvParser.parse(bad) }
        assertContains(e.message!!, "Line 2")
    }
}
