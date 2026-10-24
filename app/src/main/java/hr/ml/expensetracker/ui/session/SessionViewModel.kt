package hr.ml.expensetracker.ui.session

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import hr.ml.expensetracker.session.AuthGateway
import hr.ml.expensetracker.session.GoogleIdTokenResult
import hr.ml.expensetracker.session.SessionRepository
import hr.ml.expensetracker.session.SessionState
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SignInError { NO_ACCOUNT, FAILED }

data class SessionUiState(
    val session: SessionState = SessionState.Loading,
    val signingIn: Boolean = false,
    val signInError: SignInError? = null,
)

class SessionViewModel(
    private val session: SessionRepository,
    private val auth: AuthGateway,
) : ViewModel() {
    private val signIn = MutableStateFlow(SessionUiState())

    /**
     * Owner of the signed-in screens' ViewModels. Cleared as soon as nobody with access is signed
     * in: their Firestore listeners stop before the rules start denying them, and the next member
     * starts with fresh state (e.g. their own default payer). Survives configuration changes.
     */
    val memberViewModels: ViewModelStoreOwner = object : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }

    init {
        viewModelScope.launch {
            session.state.collect { if (it !is SessionState.Member) memberViewModels.viewModelStore.clear() }
        }
    }

    override fun onCleared() = memberViewModels.viewModelStore.clear()

    val state: StateFlow<SessionUiState> = combine(session.state, signIn) { s, ui -> ui.copy(session = s) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionUiState(session.state.value))

    /** [requestIdToken] runs the Credential Manager picker (it needs the Activity, so the UI passes it). */
    fun signIn(requestIdToken: suspend () -> GoogleIdTokenResult) {
        if (signIn.value.signingIn) return
        signIn.update { it.copy(signingIn = true, signInError = null) }
        viewModelScope.launch {
            val error = try {
                when (val result = requestIdToken()) {
                    is GoogleIdTokenResult.Success -> {
                        auth.signInWithGoogleIdToken(result.idToken)
                        null
                    }
                    GoogleIdTokenResult.Cancelled -> null
                    GoogleIdTokenResult.NoAccount -> SignInError.NO_ACCOUNT
                    is GoogleIdTokenResult.Failure -> SignInError.FAILED
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("SessionViewModel", "Firebase sign-in failed", e)
                SignInError.FAILED
            }
            signIn.update { it.copy(signingIn = false, signInError = error) }
        }
    }

    fun signOut() {
        viewModelScope.launch { session.signOut() }
    }

    fun retry() = session.retry()
}
