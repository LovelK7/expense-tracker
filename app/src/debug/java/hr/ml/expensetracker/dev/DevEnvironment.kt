package hr.ml.expensetracker.dev

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import hr.ml.expensetracker.BuildConfig
import hr.ml.expensetracker.R
import java.util.Base64

/**
 * Debug builds only (the release source set has a no-op twin). With `useFirebaseEmulators=true`
 * (local.properties or -P) Firebase talks to the local emulator suite started from `firebase.json`
 * and the sign-in screen offers one button per dev account. See README, "Local development".
 */
object DevEnvironment {
    /** The development machine as seen from the Android emulator. */
    private const val HOST = "10.0.2.2"
    private const val AUTH_PORT = 9099
    private const val FIRESTORE_PORT = 8080

    private val enabled = BuildConfig.USE_FIREBASE_EMULATORS

    /** Must run before the first use of [auth]. */
    fun configure(auth: FirebaseAuth) {
        if (enabled) auth.useEmulator(HOST, AUTH_PORT)
    }

    /** Must run before the first read or write on [db]. */
    fun configure(db: FirebaseFirestore) {
        if (enabled) db.useEmulator(HOST, FIRESTORE_PORT)
    }

    /**
     * Placeholder accounts, never real addresses. `tools/seed_members.py --emulator` seeds the
     * same emails into the emulator's `config/members`; keep the two in sync.
     */
    val accounts: List<DevAccount> = if (!enabled) {
        emptyList()
    } else {
        listOf(
            DevAccount(R.string.dev_sign_in_lovel, fakeGoogleIdToken("dev-lovel", "lovel.dev@example.com")),
            DevAccount(R.string.dev_sign_in_marina, fakeGoogleIdToken("dev-marina", "marina.dev@example.com")),
        )
    }

    /**
     * An unsigned Google ID token. The Auth emulator does not check signatures and creates (or
     * reuses) the account from the `sub`/`email` claims; real Firebase Auth would reject it.
     */
    private fun fakeGoogleIdToken(sub: String, email: String): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        fun part(json: String) = encoder.encodeToString(json.toByteArray(Charsets.UTF_8))
        val header = part("""{"alg":"none","typ":"JWT"}""")
        val payload = part("""{"sub":"$sub","email":"$email","email_verified":true}""")
        return "$header.$payload."
    }
}
