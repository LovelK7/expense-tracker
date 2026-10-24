package hr.ml.expensetracker.data

import com.google.firebase.Timestamp
import hr.ml.expensetracker.domain.Expense
import hr.ml.expensetracker.domain.Members
import hr.ml.expensetracker.domain.Person
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
    fun toDocument(e: Expense): Map<String, Any> = mapOf(
        Firestore.DATE to e.date.toIso(),
        Firestore.CATEGORY to e.category,
        Firestore.DESCRIPTION to e.description,
        Firestore.AMOUNT_CENTS to e.amountCents,
        Firestore.PAID_BY to e.paidBy.name,
        Firestore.SPLIT to e.split.name,
        Firestore.CREATED_AT to e.createdAt.toTimestamp(),
        Firestore.UPDATED_AT to e.updatedAt.toTimestamp(),
        Firestore.CREATED_BY to e.createdBy.name,
        Firestore.DELETED to e.deleted,
    )

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
