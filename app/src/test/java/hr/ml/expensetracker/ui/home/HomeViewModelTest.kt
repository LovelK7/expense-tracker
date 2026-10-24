package hr.ml.expensetracker.ui.home

import hr.ml.expensetracker.domain.Expense
import hr.ml.expensetracker.domain.ExpenseRepository
import hr.ml.expensetracker.domain.Person
import hr.ml.expensetracker.domain.Settlement
import hr.ml.expensetracker.domain.SettlementRepository
import hr.ml.expensetracker.domain.SplitType
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val created = Instant.parse("2026-10-01T12:00:00Z")

    private fun expense(id: String, cents: Long, paidBy: Person, split: SplitType) = Expense(
        id = id,
        date = LocalDate.of(2026, 10, 1),
        category = "Hrana",
        description = "",
        amountCents = cents,
        paidBy = paidBy,
        split = split,
        createdAt = created,
        updatedAt = created,
        createdBy = paidBy,
    )

    private class FakeExpenses(val flow: Flow<List<Expense>>) : ExpenseRepository {
        override fun observeAll() = flow
        override fun newId() = "id"
        override fun add(expense: Expense) = Unit
        override fun update(expense: Expense) = Unit
        override fun softDelete(id: String) = Unit
    }

    private class FakeSettlements(val flow: Flow<List<Settlement>>) : SettlementRepository {
        override fun observeAll() = flow
        override fun newId() = "id"
        override fun add(settlement: Settlement) = Unit
        override fun update(settlement: Settlement) = Unit
        override fun softDelete(id: String) = Unit
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun balanceFollowsTheRepositories() = runTest {
        val expenses = MutableStateFlow(emptyList<Expense>())
        val settlements = MutableStateFlow(emptyList<Settlement>())
        val vm = HomeViewModel(FakeExpenses(expenses), FakeSettlements(settlements), computeDispatcher = UnconfinedTestDispatcher(testScheduler))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }

        assertFalse(vm.state.value.loading)
        assertEquals(0, vm.state.value.expenseCount)
        assertEquals(0L, vm.state.value.balanceCents)

        expenses.value = listOf(
            expense("1", 100_000, Person.LOVEL, SplitType.HALF),
            expense("2", 8_540, Person.MARINA, SplitType.HALF),
            expense("3", 6_200, Person.LOVEL, SplitType.HALF),
            expense("4", 2_500, Person.MARINA, SplitType.FULL_FOR_OTHER),
            expense("5", 7_140, Person.LOVEL, SplitType.FULL_FOR_OTHER),
        )
        // -500.00 + 42.70 - 31.00 + 25.00 - 71.40: Marina owes Lovel 534,70 €
        assertEquals(-53_470L, vm.state.value.balanceCents)
        assertEquals(5, vm.state.value.expenseCount)

        settlements.value = listOf(
            Settlement("s1", LocalDate.of(2026, 10, 2), Person.MARINA, Person.LOVEL, 53_470, "", created, created, Person.MARINA),
        )
        assertEquals(0L, vm.state.value.balanceCents)
    }

    @Test
    fun startsLoadingUntilTheFirstSnapshot() = runTest {
        val vm = HomeViewModel(FakeExpenses(flow { }), FakeSettlements(MutableStateFlow(emptyList())), computeDispatcher = UnconfinedTestDispatcher(testScheduler))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        assertTrue(vm.state.value.loading)
    }

    @Test
    fun repositoryFailureShowsAnError() = runTest {
        val failing = flow<List<Expense>> { throw IllegalStateException("permission denied") }
        val vm = HomeViewModel(FakeExpenses(failing), FakeSettlements(MutableStateFlow(emptyList())), computeDispatcher = UnconfinedTestDispatcher(testScheduler))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        assertTrue(vm.state.value.error)
        assertFalse(vm.state.value.loading)
    }
}
