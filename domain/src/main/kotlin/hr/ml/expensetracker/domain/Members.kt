package hr.ml.expensetracker.domain

/**
 * The allow-list of Google accounts that may use the app and which [Person] each one is.
 * Mirrors the Firestore document `config/members`. Emails are compared trimmed and lowercase.
 */
class Members(emails: Collection<String>, persons: Map<String, Person>) {
    val emails: Set<String> = emails.mapTo(LinkedHashSet()) { normalize(it) }
    val persons: Map<String, Person> = persons.mapKeys { (email, _) -> normalize(email) }

    /** The person behind [email], or null when the account is not an allow-listed member. */
    fun personFor(email: String?): Person? {
        if (email.isNullOrBlank()) return null
        val key = normalize(email)
        return if (key in emails) persons[key] else null
    }

    override fun equals(other: Any?) =
        other is Members && emails == other.emails && persons == other.persons

    override fun hashCode() = 31 * emails.hashCode() + persons.hashCode()

    // No addresses here: this string can end up in logs.
    override fun toString() = "Members(emails=${emails.size}, persons=${persons.size})"

    companion object {
        fun normalize(email: String): String = email.trim().lowercase()
    }
}
