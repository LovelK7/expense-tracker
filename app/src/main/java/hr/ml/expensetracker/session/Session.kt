package hr.ml.expensetracker.session

import hr.ml.expensetracker.domain.Members
import hr.ml.expensetracker.domain.Person
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** The Firebase user, reduced to what the app needs. */
data class AuthUser(val uid: String, val email: String?)

/** Firebase Auth behind an interface so the session logic is testable without Android. */
interface AuthGateway {
    /** The signed-in user, or null; emits on every sign-in and sign-out. */
    val user: Flow<AuthUser?>

    suspend fun signInWithGoogleIdToken(idToken: String)

    suspend fun signOut()
}

/** What reading `config/members` produced. */
sealed interface MembersSnapshot {
    data class Loaded(val members: Members) : MembersSnapshot

    /** Rules refused the read: the signed-in account is not a member. */
    data object Denied : MembersSnapshot

    /** Offline and nothing cached yet; a server answer may still come. */
    data object Pending : MembersSnapshot

    data class Failed(val cause: Throwable) : MembersSnapshot
}

interface MembersSource {
    /** Live view of `config/members` for the currently signed-in user. */
    fun observe(): Flow<MembersSnapshot>
}

sealed interface SessionState {
    data object Loading : SessionState

    data object SignedOut : SessionState

    /** Signed in with an account that is not on the allow-list. */
    data class NoAccess(val email: String?) : SessionState

    data class Member(val email: String, val person: Person) : SessionState

    /** Membership could not be checked (unexpected Firestore error); the user can retry. */
    data object Error : SessionState

    companion object {
        /** Maps a signed-in account to its [Person] via the allow-list. */
        fun resolve(email: String?, members: Members): SessionState {
            val person = members.personFor(email) ?: return NoAccess(email)
            return Member(Members.normalize(email!!), person)
        }
    }
}

/**
 * Who is using the app: signed out, signed in but not allow-listed, or a member with a [Person].
 * The member's person is what new records carry as `createdBy`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionRepository(
    private val auth: AuthGateway,
    private val members: MembersSource,
    scope: CoroutineScope,
) {
    private val retries = MutableStateFlow(0)

    val state: StateFlow<SessionState> = combine(auth.user, retries) { user, _ -> user }
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(SessionState.SignedOut)
            } else {
                members.observe()
                    .map { snapshot -> toState(user, snapshot) }
                    .onStart { emit(SessionState.Loading) }
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, SessionState.Loading)

    /** The signed-in member's person, or null when nobody with access is signed in. */
    val currentPerson: Person?
        get() = (state.value as? SessionState.Member)?.person

    fun retry() = retries.update { it + 1 }

    suspend fun signOut() = auth.signOut()

    private fun toState(user: AuthUser, snapshot: MembersSnapshot): SessionState = when (snapshot) {
        is MembersSnapshot.Loaded -> SessionState.resolve(user.email, snapshot.members)
        MembersSnapshot.Denied -> SessionState.NoAccess(user.email)
        MembersSnapshot.Pending -> SessionState.Loading
        is MembersSnapshot.Failed -> SessionState.Error
    }
}
