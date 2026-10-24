package hr.ml.expensetracker.ui.session

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import hr.ml.expensetracker.BuildConfig
import hr.ml.expensetracker.R
import hr.ml.expensetracker.dev.DevAccount
import hr.ml.expensetracker.ui.theme.TroskoviTheme

@Composable
private fun CenteredColumn(content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) { content() }
        }
    }
}

@Composable
fun LoadingScreen() {
    CenteredColumn {
        CircularProgressIndicator()
        Text(stringResource(R.string.loading), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun SignInScreen(
    signingIn: Boolean,
    error: SignInError?,
    onSignIn: () -> Unit,
    devAccounts: List<DevAccount> = emptyList(),
    onDevSignIn: (DevAccount) -> Unit = {},
) {
    CenteredColumn {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall)
        Text(
            stringResource(R.string.sign_in_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(Modifier.height(48.dp), contentAlignment = Alignment.Center) {
            if (signingIn) {
                CircularProgressIndicator(Modifier.size(32.dp))
            } else {
                Button(onClick = onSignIn) { Text(stringResource(R.string.sign_in_button)) }
            }
        }
        if (!signingIn) {
            devAccounts.forEach { account ->
                OutlinedButton(onClick = { onDevSignIn(account) }) { Text(stringResource(account.label)) }
            }
        }
        if (error != null) {
            @StringRes val message = when (error) {
                SignInError.NO_ACCOUNT -> R.string.sign_in_error_no_account
                SignInError.FAILED -> R.string.sign_in_error_failed
            }
            Text(
                stringResource(message),
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text("v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun NoAccessScreen(email: String?, onSignOut: () -> Unit) {
    CenteredColumn {
        Text(
            stringResource(R.string.no_access_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        if (email != null) {
            Text(
                stringResource(R.string.no_access_body, email),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = onSignOut) { Text(stringResource(R.string.sign_out)) }
    }
}

@Composable
fun SessionErrorScreen(onRetry: () -> Unit, onSignOut: () -> Unit) {
    CenteredColumn {
        Text(
            stringResource(R.string.session_error_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        OutlinedButton(onClick = onSignOut) { Text(stringResource(R.string.sign_out)) }
    }
}

@Preview(showBackground = true)
@Composable
private fun SignInScreenPreview() {
    TroskoviTheme {
        SignInScreen(signingIn = false, error = SignInError.NO_ACCOUNT, onSignIn = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun NoAccessScreenPreview() {
    TroskoviTheme {
        NoAccessScreen(email = "netko@example.com", onSignOut = {})
    }
}
