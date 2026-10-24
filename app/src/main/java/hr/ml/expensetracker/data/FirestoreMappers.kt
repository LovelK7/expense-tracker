package hr.ml.expensetracker.data

import com.google.firebase.Timestamp
import hr.ml.expensetracker.domain.Expense
import hr.ml.expensetracker.domain.Members
import hr.ml.expensetracker.domain.Person
import hr.ml.expensetracker.domain.RecurringTemplate
import hr.ml.expensetracker.domain.Settlement
import hr.ml.expensetracker.domain.SplitType
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Firestore document layout. The record id is the document id and is not stored as a field.
 * `firestore.rules` validates exactly these fields; keep the two in sync.
 */
object Firestore {
    const val EXPENSES = "expenses"
    const val SETTLEMENTS = "settlements"
    const val RECURRING = "recurring"
    const val CONFIG = "config"
    const val MEMBERS_DOC = "members"

    const val DATE = "date"
    const val CATEGORY = "category"
    const val DESCRIPTION = "description"
    const val AMOUNT_CENTS = "amountCents"
    const val PAID_BY = "paidBy"
    const val SPLIT = "split"
    const val FROM = "from"
    const val TO = "to"
    const val NOTE = "note"
    const val CREATED_AT = "createdAt"
    const val UPDATED_AT = "updatedAt"
    const val CREATED_BY = "createdBy"
    const val DELETED = "deleted"

    // Expenses posted from a recurring template: both or neither.
    const val TEMPLATE_ID = "templateId"
    const val PERIOD = "period"

    // recurring/{id}
    const val NAME = "name"
    const val DAY_OF_MONTH = "dayOfMonth"
    const val ACTIVE = "active"
    const val SKIPPED_PERIODS = "skippedPeriods"

    const val EMAILS = "emails"
    const val PERSONS = "persons"
}

internal fun Instant.toTimestamp() = Timestamp(epochSecond, nano)


private val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

private fun LocalDate.toIso(): String = format(ISO_DATE)

/** Reads typed fields out of a document map, failing with the field name when one is wrong. */
private class Reader(private val data: Map<String, Any?>) {
    fun string(key: String): String = data[key] as? String ?: bad(key)

    fun long(key: String): Long = when (val v = data[key]) {
        is Long -> v
        is Int -> v.toLong()
        else -> bad(key)
    }

    fun bool(key: String): Boolean = data[key] as? Boolean ?: bad(key)

    /** An optional string: absent (or null) reads as null. */
    fun optionalString(key: String): String? = when (val v = data[key]) {
        null -> null
        is String -> v
        else -> bad(key)
    }

    fun int(key: String): Int {
        val v = long(key)
        if (v !in Int.MIN_VALUE..Int.MAX_VALUE) bad(key)
        return v.toInt()
    }

    fun strings(key: String): List<String> {
        val list = data[key] as? List<*> ?: bad(key)
        return list.map { it as? String ?: bad(key) }
    }

    fun date(key: String): LocalDate = try {
        LocalDate.parse(string(key), ISO_DATE)
    } catch (e: DateTimeParseException) {
        throw IllegalArgumentException("field '$key' is not an ISO date", e)
    }

    fun instant(key: String): Instant = (data[key] as? Timestamp ?: bad(key)).toInstant()

    fun person(key: String): Person = Person.valueOf(string(key))

    fun split(key: String): SplitType = SplitType.valueOf(string(key))

    private fun bad(key: String): Nothing =
        throw IllegalArgumentException("field '$key' is missing or has the wrong type")
}

object ExpenseMapper {
    /** [Expense.templateId] and [Expense.period] are written only when set (never on legacy rows). */
    fun toDocument(e: Expense): Map<String, Any> = buildMap {
        put(Firestore.DATE, e.date.toIso())
        put(Firestore.CATEGORY, e.category)
        put(Firestore.DESCRIPTION, e.description)
        put(Firestore.AMOUNT_CENTS, e.amountCents)
        put(Firestore.PAID_BY, e.paidBy.name)
        put(Firestore.SPLIT, e.split.name)
        put(Firestore.CREATED_AT, e.createdAt.toTimestamp())
        put(Firestore.UPDATED_AT, e.updatedAt.toTimestamp())
        put(Firestore.CREATED_BY, e.createdBy.name)
        put(Firestore.DELETED, e.deleted)
        e.templateId?.let { put(Firestore.TEMPLATE_ID, it) }
        e.period?.let { put(Firestore.PERIOD, it) }
    }

    /** @throws IllegalArgumentException when a field is missing or malformed. */
    fun fromDocument(id: String, data: Map<String, Any?>): Expense = with(Reader(data)) {
        Expense(
            id = id,
            date = date(Firestore.DATE),
            category = string(Firestore.CATEGORY),
            description = string(Firestore.DESCRIPTION),
            amountCents = long(Firestore.AMOUNT_CENTS),
            paidBy = person(Firestore.PAID_BY),
            split = split(Firestore.SPLIT),
            createdAt = instant(Firestore.CREATED_AT),
            updatedAt = instant(Firestore.UPDATED_AT),
            createdBy = person(Firestore.CREATED_BY),
            deleted = bool(Firestore.DELETED),
            templateId = optionalString(Firestore.TEMPLATE_ID),
            period = optionalString(Firestore.PERIOD),
        )
    }
}

object SettlementMapper {
    fun toDocument(s: Settlement): Map<String, Any> = mapOf(
        Firestore.DATE to s.date.toIso(),
        Firestore.FROM to s.from.name,
        Firestore.TO to s.to.name,
        Firestore.AMOUNT_CENTS to s.amountCents,
        Firestore.NOTE to s.note,
        Firestore.CREATED_AT to s.createdAt.toTimestamp(),
        Firestore.UPDATED_AT to s.updatedAt.toTimestamp(),
        Firestore.CREATED_BY to s.createdBy.name,
        Firestore.DELETED to s.deleted,
    )

    /** @throws IllegalArgumentException when a field is missing or malformed. */
    fun fromDocument(id: String, data: Map<String, Any?>): Settlement = with(Reader(data)) {
        Settlement(
            id = id,
            date = date(Firestore.DATE),
            from = person(Firestore.FROM),
            to = person(Firestore.TO),
            amountCents = long(Firestore.AMOUNT_CENTS),
            note = string(Firestore.NOTE),
            createdAt = instant(Firestore.CREATED_AT),
            updatedAt = instant(Firestore.UPDATED_AT),
            createdBy = person(Firestore.CREATED_BY),
            deleted = bool(Firestore.DELETED),
        )
    }
}

/** `recurring/{id}`: a [RecurringTemplate]; `skippedPeriods` is a sorted list of "yyyy-MM" strings. */
object RecurringMapper {
    fun toDocument(t: RecurringTemplate): Map<String, Any> = mapOf(
        Firestore.NAME to t.name,
        Firestore.CATEGORY to t.category,
        Firestore.DESCRIPTION to t.description,
        Firestore.AMOUNT_CENTS to t.amountCents,
        Firestore.PAID_BY to t.paidBy.name,
        Firestore.SPLIT to t.split.name,
        Firestore.DAY_OF_MONTH to t.dayOfMonth.toLong(),
        Firestore.ACTIVE to t.active,
        Firestore.SKIPPED_PERIODS to t.skippedPeriods.sorted(),
        Firestore.CREATED_AT to t.createdAt.toTimestamp(),
        Firestore.UPDATED_AT to t.updatedAt.toTimestamp(),
        Firestore.CREATED_BY to t.createdBy.name,
        Firestore.DELETED to t.deleted,
    )

    /** @throws IllegalArgumentException when a field is missing or malformed. */
    fun fromDocument(id: String, data: Map<String, Any?>): RecurringTemplate = with(Reader(data)) {
        RecurringTemplate(
            id = id,
            name = string(Firestore.NAME),
            category = string(Firestore.CATEGORY),
            description = string(Firestore.DESCRIPTION),
            amountCents = long(Firestore.AMOUNT_CENTS),
            paidBy = person(Firestore.PAID_BY),
            split = split(Firestore.SPLIT),
            dayOfMonth = int(Firestore.DAY_OF_MONTH),
            active = bool(Firestore.ACTIVE),
            skippedPeriods = strings(Firestore.SKIPPED_PERIODS).toSet(),
            createdAt = instant(Firestore.CREATED_AT),
            updatedAt = instant(Firestore.UPDATED_AT),
            createdBy = person(Firestore.CREATED_BY),
            deleted = bool(Firestore.DELETED),
        )
    }
}

/**
 * `config/members` is `{ emails: [string], persons: { "<email>": "LOVEL" | "MARINA" } }`.
 * Dots in the email map keys are fine: they are plain map keys, never parsed as field paths.
 */
object MembersMapper {
    /** @throws IllegalArgumentException when `emails` or `persons` is missing or not the right shape. */
    fun fromDocument(data: Map<String, Any?>): Members {
        val emails = data[Firestore.EMAILS] as? List<*>
            ?: throw IllegalArgumentException("field 'emails' is missing or not a list")
        val persons = data[Firestore.PERSONS] as? Map<*, *>
            ?: throw IllegalArgumentException("field 'persons' is missing or not a map")
        return Members(
            emails = emails.filterIsInstance<String>(),
            // Entries with an unknown person name are ignored, so that account resolves to no access.
            persons = persons.entries.mapNotNull { (email, person) ->
                val p = Person.entries.firstOrNull { it.name == person }
                if (email is String && p != null) email to p else null
            }.toMap(),
        )
    }
}
