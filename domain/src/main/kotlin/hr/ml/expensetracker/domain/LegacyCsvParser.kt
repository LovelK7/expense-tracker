package hr.ml.expensetracker.domain

import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

/**
 * Reads the CSV written by `tools/export_legacy.py`:
 *
 * ```
 * date,category,description,amount_cents,paid_by,split
 * 2023-01-02,Hrana,Spar Martinkovac,6720,MARINA,HALF
 * ```
 *
 * RFC 4180 quoting is supported; CRLF line endings are read as LF. Categories are kept exactly as written unless a
 * [CategoryNormalizer] is supplied. Ids are `legacy-<row>` (1-based data row number);
 * timestamps are the expense date at 00:00 UTC and `createdBy` is the payer.
 */
object LegacyCsvParser {

    val HEADER = listOf("date", "category", "description", "amount_cents", "paid_by", "split")

    fun parse(csv: String, normalizer: CategoryNormalizer? = null): List<Expense> {
        val records = readRecords(csv.removePrefix("\uFEFF").replace("\r\n", "\n"))
        require(records.isNotEmpty()) { "Empty CSV" }
        val (headerLine, header) = records.first()
        require(header.map { it.trim() } == HEADER) { "Line $headerLine: unexpected header $header, expected $HEADER" }

        return records.drop(1).mapIndexed { index, (line, fields) ->
            try {
                toExpense(index + 1, fields, normalizer)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Line $line: ${e.message}", e)
            } catch (e: DateTimeParseException) {
                throw IllegalArgumentException("Line $line: ${e.message}", e)
            }
        }
    }

    private fun toExpense(row: Int, f: List<String>, normalizer: CategoryNormalizer?): Expense {
        require(f.size == HEADER.size) { "expected ${HEADER.size} fields, got ${f.size}" }
        val date = LocalDate.parse(f[0].trim())
        val amount = f[3].trim().toLongOrNull() ?: throw IllegalArgumentException("bad amount_cents '${f[3]}'")
        val paidBy = enumValue<Person>(f[4], "paid_by")
        val split = enumValue<SplitType>(f[5], "split")
        val instant = date.atStartOfDay().toInstant(ZoneOffset.UTC)
        return Expense(
            id = "legacy-%04d".format(row),
            date = date,
            category = normalizer?.normalize(f[1]) ?: f[1],
            description = f[2],
            amountCents = amount,
            paidBy = paidBy,
            split = split,
            createdAt = instant,
            updatedAt = instant,
            createdBy = paidBy,
        )
    }

    private inline fun <reified T : Enum<T>> enumValue(raw: String, column: String): T =
        enumValues<T>().firstOrNull { it.name == raw.trim() }
            ?: throw IllegalArgumentException("bad $column '$raw'")

    /** Splits [text] into (starting line number, fields) records, honouring quoted fields. */
    private fun readRecords(text: String): List<Pair<Int, List<String>>> {
        val records = mutableListOf<Pair<Int, List<String>>>()
        val fields = mutableListOf<String>()
        val field = StringBuilder()
        var line = 1
        var recordStart = 1
        var inQuotes = false
        var i = 0

        fun endRecord() {
            fields += field.toString()
            field.clear()
            if (!(fields.size == 1 && fields[0].isEmpty())) records += recordStart to fields.toList()
            fields.clear()
        }

        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                when {
                    c == '"' && i + 1 < text.length && text[i + 1] == '"' -> { field.append('"'); i++ }
                    c == '"' -> inQuotes = false
                    else -> { if (c == '\n') line++; field.append(c) }
                }
            } else {
                when (c) {
                    '"' -> inQuotes = true
                    ',' -> { fields += field.toString(); field.clear() }
                    '\n' -> { endRecord(); line++; recordStart = line }
                    else -> field.append(c)
                }
            }
            i++
        }
        require(!inQuotes) { "Unterminated quoted field starting on line $recordStart" }
        if (field.isNotEmpty() || fields.isNotEmpty()) endRecord()
        return records
    }
}
