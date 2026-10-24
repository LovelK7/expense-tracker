package hr.ml.expensetracker.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hr.ml.expensetracker.R
import hr.ml.expensetracker.domain.Money
import hr.ml.expensetracker.ui.theme.TroskoviTheme
import kotlin.math.absoluteValue
import org.koin.androidx.compose.koinViewModel

@Composable
fun HomeScreen(modifier: Modifier = Modifier, viewModel: HomeViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeContent(state, modifier)
}

@Composable
private fun HomeContent(state: HomeUiState, modifier: Modifier = Modifier) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        BalanceCard(state.balanceCents)
        Text(
            text = pluralStringResource(R.plurals.balance_sample_note, state.expenseCount, state.expenseCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BalanceCard(balanceCents: Long) {
    val amount = Money.format(balanceCents.absoluteValue)
    val text = when {
        balanceCents > 0 -> stringResource(R.string.balance_lovel_owes, amount)
        balanceCents < 0 -> stringResource(R.string.balance_marina_owes, amount)
        else -> stringResource(R.string.balance_settled)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.balance_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeContentPreview() {
    TroskoviTheme(dynamicColor = false) {
        HomeContent(HomeUiState(balanceCents = -53_470, expenseCount = 5))
    }
}
