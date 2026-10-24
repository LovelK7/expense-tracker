package hr.ml.expensetracker.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hr.ml.expensetracker.R
import hr.ml.expensetracker.domain.Person
import hr.ml.expensetracker.ui.common.label
import hr.ml.expensetracker.ui.expense.AddExpenseScreen
import hr.ml.expensetracker.ui.expense.EditExpenseScreen
import hr.ml.expensetracker.ui.home.HomeScreen
import hr.ml.expensetracker.ui.home.HomeSearchField
import hr.ml.expensetracker.ui.home.HomeTags
import hr.ml.expensetracker.ui.home.HomeViewModel
import hr.ml.expensetracker.ui.stats.StatisticsScreen
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

private enum class Destination(@param:StringRes val label: Int, @param:DrawableRes val icon: Int) {
    HOME(R.string.nav_home, R.drawable.ic_home),
    ADD(R.string.nav_add, R.drawable.ic_add),
    STATS(R.string.nav_stats, R.drawable.ic_bar_chart),
}

/** The signed-in app: three tabs, plus the expense editor layered over them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShell(person: Person, onSignOut: () -> Unit) {
    var current by rememberSaveable { mutableStateOf(Destination.HOME) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val savedMessage = stringResource(R.string.expense_saved)
    val onSaved: () -> Unit = {
        current = Destination.HOME
        editingId = null
        scope.launch { snackbar.showSnackbar(savedMessage) }
    }

    // The same instance HomeScreen gets: the top bar hosts its search field.
    val home: HomeViewModel = koinViewModel()
    val searchOpen by home.searchOpen.collectAsStateWithLifecycle()
    val searching = searchOpen && current == Destination.HOME
    BackHandler(enabled = searching && editingId == null, onBack = home::closeSearch)

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                if (searching) {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = home::closeSearch, modifier = Modifier.testTag(HomeTags.SEARCH_CLOSE)) {
                                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.search_close))
                            }
                        },
                        title = {
                            HomeSearchField(
                                text = home.searchText,
                                onTextChange = home::onSearchTextChange,
                                onClear = home::clearSearch,
                            )
                        },
                    )
                } else {
                    MainTopBar(
                        person = person,
                        showSearch = current == Destination.HOME,
                        onSearch = home::openSearch,
                        onSignOut = onSignOut,
                    )
                }
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
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            val modifier = Modifier.padding(padding).consumeWindowInsets(padding)
            when (current) {
                Destination.HOME -> HomeScreen(
                    person = person,
                    snackbar = snackbar,
                    scope = scope,
                    onEditExpense = { editingId = it },
                    modifier = modifier,
                    viewModel = home,
                )
                Destination.ADD -> AddExpenseScreen(onSaved = onSaved, modifier = modifier)
                Destination.STATS -> StatisticsScreen(modifier = modifier)
            }
        }
        editingId?.let { id ->
            EditExpenseScreen(expenseId = id, onClose = { editingId = null }, onSaved = onSaved)
        }
    }
}

/** App name, the search button (on "Početna"), the signed-in person and sign-out. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTopBar(person: Person, showSearch: Boolean, onSearch: () -> Unit, onSignOut: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(R.string.app_name)) },
        actions = {
            if (showSearch) {
                IconButton(onClick = onSearch, modifier = Modifier.testTag(HomeTags.SEARCH_OPEN)) {
                    Icon(painterResource(R.drawable.ic_search), contentDescription = stringResource(R.string.search))
                }
            }
            Text(
                text = stringResource(person.label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
            IconButton(onClick = onSignOut) {
                Icon(painterResource(R.drawable.ic_logout), contentDescription = stringResource(R.string.sign_out))
            }
        },
    )
}
