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
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import hr.ml.expensetracker.ui.home.HomeScreen
import hr.ml.expensetracker.ui.theme.TroskoviTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TroskoviTheme {
                AppShell()
            }
        }
    }
}

private enum class Destination(@param:StringRes val label: Int, @param:DrawableRes val icon: Int) {
    HOME(R.string.nav_home, R.drawable.ic_home),
    ADD(R.string.nav_add, R.drawable.ic_add),
    STATS(R.string.nav_stats, R.drawable.ic_bar_chart),
}

@Composable
private fun AppShell() {
    var current by rememberSaveable { mutableStateOf(Destination.HOME) }
    Scaffold(
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
