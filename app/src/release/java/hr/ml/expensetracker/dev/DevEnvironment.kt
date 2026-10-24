package hr.ml.expensetracker.dev

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/** Release builds: always the real Firebase project, no dev sign-in (see the debug source set). */
object DevEnvironment {
    @Suppress("UNUSED_PARAMETER")
    fun configure(auth: FirebaseAuth) = Unit

    @Suppress("UNUSED_PARAMETER")
    fun configure(db: FirebaseFirestore) = Unit

    val accounts: List<DevAccount> = emptyList()
}
