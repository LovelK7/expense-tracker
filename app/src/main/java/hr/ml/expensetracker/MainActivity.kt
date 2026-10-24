package hr.ml.expensetracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import hr.ml.expensetracker.dev.DevEnvironment
import hr.ml.expensetracker.session.GoogleIdTokenResult
import hr.ml.expensetracker.session.GoogleSignIn
import hr.ml.expensetracker.session.SessionState
import hr.ml.expensetracker.ui.AppShell
import hr.ml.expensetracker.ui.session.LoadingScreen
import hr.ml.expensetracker.ui.session.NoAccessScreen
import hr.ml.expensetracker.ui.session.SessionErrorScreen
import hr.ml.expensetracker.ui.session.SessionViewModel
import hr.ml.expensetracker.ui.session.SignInScreen
import hr.ml.expensetracker.ui.theme.TroskoviTheme
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TroskoviTheme {
                AppRoot()
            }
        }
    }
}

/** Gate: sign-in and membership first, then the app itself. */
@Composable
private fun AppRoot(viewModel: SessionViewModel = koinViewModel(), googleSignIn: GoogleSignIn = koinInject()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activityContext = LocalContext.current
    when (val session = state.session) {
        SessionState.Loading -> LoadingScreen()
        SessionState.SignedOut -> SignInScreen(
            signingIn = state.signingIn,
            error = state.signInError,
            onSignIn = { viewModel.signIn { googleSignIn.requestIdToken(activityContext) } },
            // Empty except in debug builds pointed at the Firebase emulators.
            devAccounts = DevEnvironment.accounts,
            onDevSignIn = { account -> viewModel.signIn { GoogleIdTokenResult.Success(account.idToken) } },
        )
        is SessionState.NoAccess -> NoAccessScreen(session.email, onSignOut = viewModel::signOut)
        SessionState.Error -> SessionErrorScreen(onRetry = viewModel::retry, onSignOut = viewModel::signOut)
        is SessionState.Member ->
            // Member screens' ViewModels live only as long as this member's session.
            CompositionLocalProvider(LocalViewModelStoreOwner provides viewModel.memberViewModels) {
                AppShell(session.person, onSignOut = viewModel::signOut)
            }
    }
}
