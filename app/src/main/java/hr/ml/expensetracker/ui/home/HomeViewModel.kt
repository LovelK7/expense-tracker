package hr.ml.expensetracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hr.ml.expensetracker.domain.BalanceCalculator
import hr.ml.expensetracker.domain.ExpenseRepository
import hr.ml.expensetracker.domain.SettlementRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val loading: Boolean = true,
    /** Positive: Lovel owes Marina. Negative: Marina owes Lovel. */
    val balanceCents: Long = 0,
    val expenseCount: Int = 0,
    val error: Boolean = false,
)

class HomeViewModel(expenses: ExpenseRepository, settlements: SettlementRepository) : ViewModel() {
    val state: StateFlow<HomeUiState> =
        combine(expenses.observeAll(), settlements.observeAll()) { e, s ->
            HomeUiState(
                loading = false,
                balanceCents = BalanceCalculator.balanceCents(e, s),
                expenseCount = e.count { !it.deleted },
            )
        }
            .catch { emit(HomeUiState(loading = false, error = true)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
