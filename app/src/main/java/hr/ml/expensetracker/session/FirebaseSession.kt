package hr.ml.expensetracker.session

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.exceptions.ClearCredentialException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import hr.ml.expensetracker.data.Firestore
import hr.ml.expensetracker.data.MembersMapper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await

private const val TAG = "Session"

class FirebaseAuthGateway(
    private val auth: FirebaseAuth,
    private val appContext: Context,
) : AuthGateway {
    override val user: Flow<AuthUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { a ->
            trySend(a.currentUser?.let { AuthUser(it.uid, it.email) })
        }
        auth.addAuthStateListener(listener) // also fires once right away with the current state
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    override suspend fun signInWithGoogleIdToken(idToken: String) {
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
    }

    override suspend fun signOut() {
        auth.signOut()
        // Forget the chosen Google account so the next sign-in shows the account picker again.
        try {
            CredentialManager.create(appContext).clearCredentialState(ClearCredentialStateRequest())
        } catch (e: ClearCredentialException) {
            Log.w(TAG, "clearCredentialState failed", e)
        }
    }
}

class FirestoreMembersSource(private val db: FirebaseFirestore) : MembersSource {
    override fun observe(): Flow<MembersSnapshot> = callbackFlow {
        val registration = db.collection(Firestore.CONFIG).document(Firestore.MEMBERS_DOC)
            .addSnapshotListener { snapshot, error ->
                val result = when {
                    error?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED -> MembersSnapshot.Denied
                    error != null -> MembersSnapshot.Failed(error)
                    snapshot == null -> MembersSnapshot.Pending
                    snapshot.exists() -> runCatching { MembersMapper.fromDocument(snapshot.data.orEmpty()) }
                        .fold({ MembersSnapshot.Loaded(it) }, { MembersSnapshot.Failed(it) })
                    // Not in the offline cache yet: wait for the server.
                    snapshot.metadata.isFromCache -> MembersSnapshot.Pending
                    // The server says it does not exist; the rules deny everyone in that case,
                    // so this is effectively "no access".
                    else -> MembersSnapshot.Denied
                }
                if (result is MembersSnapshot.Failed) Log.w(TAG, "config/members failed", result.cause)
                trySend(result)
            }
        awaitClose { registration.remove() }
    }
}
