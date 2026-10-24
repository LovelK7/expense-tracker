package hr.ml.expensetracker.dev

import androidx.annotation.StringRes

/**
 * A shortcut sign-in button for local development against the Firebase Auth emulator. Only the
 * debug build's `DevEnvironment` creates these (and only with `useFirebaseEmulators=true`);
 * release builds have none.
 */
data class DevAccount(@param:StringRes val label: Int, val idToken: String)
