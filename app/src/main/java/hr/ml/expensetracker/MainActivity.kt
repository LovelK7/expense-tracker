package hr.ml.expensetracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hr.ml.expensetracker.domain.Person
import hr.ml.expensetracker.session.GoogleSignIn
import hr.ml.expensetracker.session.SessionState
import hr.ml.expensetracker.ui.home.HomeScreen
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
        )
        is SessionState.NoAccess -> NoAccessScreen(session.email, onSignOut = viewModel::signOut)
        SessionState.Error -> SessionErrorScreen(onRetry = viewModel::retry, onSignOut = viewModel::signOut)
        is SessionState.Member -> AppShell(session.person, onSignOut = viewModel::signOut)
    }
}

private enum class Destination(@param:StringRes val label: Int, @param:DrawableRes val icon: Int) {
    HOME(R.string.nav_home, R.drawable.ic_home),
    ADD(R.string.nav_add, R.drawable.ic_add),
    STATS(R.string.nav_stats, R.drawable.ic_bar_chart),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppShell(person: Person, onSignOut: () -> Unit) {
    var current by rememberSaveable { mutableStateOf(Destination.HOME) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    Text(
                        stringResource(
                            when (person) {
                                Person.LOVEL -> R.string.person_lovel
                                Person.MARINA -> R.string.person_marina
                            },
                        ),
                    )
                    IconButton(onClick = onSignOut) {
                        Icon(painterResource(R.drawable.ic_logout), contentDescription = stringResource(R.string.sign_out))
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { dest ->
                    NavigationBarItem(
                        selected = dest == current,
                        onClick = { current = dest },
                        icon = { Icon(painterResource(dest.icon), contentDescription = null) },
                        label = { Text(stringResource(dest.label)) },
                    )
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (current) {
            Destination.HOME -> HomeScreen(modifier)
            Destination.ADD -> Placeholder(R.string.add_placeholder, modifier)
            Destination.STATS -> Placeholder(R.string.stats_placeholder, modifier)
        }
    }
}

@Composable
private fun Placeholder(@StringRes text: Int, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(text))
    }
}
