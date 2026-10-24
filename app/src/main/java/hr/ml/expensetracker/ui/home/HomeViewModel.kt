package hr.ml.expensetracker.ui.home

import androidx.lifecycle.ViewModel
import hr.ml.expensetracker.domain.BalanceCalculator
import hr.ml.expensetracker.domain.Expense
import hr.ml.expensetracker.domain.Settlement
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HomeUiState(
    /** Positive: Lovel owes Marina. Negative: Marina owes Lovel. */
    val balanceCents: Long,
    val expenseCount: Int,
)

class HomeViewModel(expenses: List<Expense>, settlements: List<Settlement>) : ViewModel() {
    private val _state = MutableStateFlow(
        HomeUiState(
            balanceCents = BalanceCalculator.balanceCents(expenses, settlements),
            expenseCount = expenses.count { !it.deleted },
        ),
    )
    val state: StateFlow<HomeUiState> = _state.asStateFlow()
}
