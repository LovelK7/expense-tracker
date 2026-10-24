package hr.ml.expensetracker.ui.stats

import hr.ml.expensetracker.domain.AnalyticsRange
import hr.ml.expensetracker.domain.Categories
import hr.ml.expensetracker.domain.Expense
import hr.ml.expensetracker.domain.ExpenseRepository
import hr.ml.expensetracker.domain.MonthWindow
import hr.ml.expensetracker.domain.Person
import hr.ml.expensetracker.domain.SplitType
import hr.ml.expensetracker.testing.InMemoryExpenseRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Trendovi and Navike in [StatisticsViewModel]: inner tab, range and person state. */
@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsViewModelTest {
    private val now = Instant.parse("2026-10-24T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneId.of("Europe/Zagreb"))
    private val expenses = InMemoryExpenseRepository(clock = clock::instant)
    private var seq = 0

    private fun add(cents: Long, date: LocalDate, category: String = "Hrana", paidBy: Person = Person.LOVEL, description: String = "") {
        val at = now.minusSeconds(3_600)
        expenses.add(Expense("e${++seq}", date, category, description, cents, paidBy, SplitType.HALF, at, at, paidBy))
    }

    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    private fun TestScope.viewModel(repo: ExpenseRepository = expenses): StatisticsViewModel {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val vm = StatisticsViewModel(repo, clock, computeDispatcher = dispatcher)
        backgroundScope.launch(dispatcher) { vm.trends.collect {} }
        backgroundScope.launch(dispatcher) { vm.habits.collect {} }
        backgroundScope.launch(dispatcher) { vm.state.collect {} }
        return vm
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun defaultsToBrojkeAnd24MonthsAndKeepsTheTab() = runTest {
        val vm = viewModel()
        assertEquals(StatsTab.NUMBERS, vm.selectedTab.value)
        assertEquals(AnalyticsSelection(AnalyticsRange.MONTHS_24, null), vm.analyticsSelection.value)
        vm.setTab(StatsTab.HABITS)
        assertEquals(StatsTab.HABITS, vm.selectedTab.value)
        vm.setTab(StatsTab.TRENDS)
        assertEquals(StatsTab.TRENDS, vm.selectedTab.value)
    }

    @Test
    fun loadingUntilTheFirstSnapshotThenEmptyWithoutData() = runTest {
        val never = object : ExpenseRepository by expenses {
            override fun observeAll(): Flow<List<Expense>> = MutableSharedFlow()
        }
        assertTrue(viewModel(never).trends.value.loading)
        val vm = viewModel()
        assertFalse(vm.trends.value.loading)
        assertTrue(vm.trends.value.empty)
        assertNull(vm.trends.value.report)
        assertTrue(vm.habits.value.empty)
    }

    @Test
    fun trendsCoverTheRangeAndLeaveOutTransfers() = runTest {
        add(1_000, d(2023, 1, 5))
        add(2_000, d(2026, 10, 2))
        add(50_000, d(2026, 10, 3), Categories.TRANSFER)
        val vm = viewModel()
        val report = vm.trends.value.report!!
        assertEquals(MonthWindow(YearMonth.of(2024, 11), YearMonth.of(2026, 10)), report.window)
        assertEquals(2_000L, report.totalCents)
        assertEquals(24, report.monthly.size)

        vm.setRange(AnalyticsRange.ALL)
        val all = vm.trends.value
        assertEquals(AnalyticsRange.ALL, all.selection.range)
        assertEquals(3_000L, all.report!!.totalCents)
        assertEquals(46, all.report.monthly.size)
        // The range is shared with Navike.
        assertEquals(AnalyticsRange.ALL, vm.habits.value.selection.range)
        assertEquals(2, vm.habits.value.report!!.tiles.expenseCount)

        vm.setRange(AnalyticsRange.MONTHS_12)
        assertEquals(MonthWindow(YearMonth.of(2025, 11), YearMonth.of(2026, 10)), vm.trends.value.report!!.window)
    }

    @Test
    fun thePersonSelectionAppliesToAllInnerTabs() = runTest {
        add(1_000, d(2026, 10, 2), paidBy = Person.LOVEL)
        add(3_000, d(2026, 10, 3), paidBy = Person.MARINA)
        val vm = viewModel()
        vm.setPerson(Person.MARINA)
        assertEquals(Person.MARINA, vm.state.value.person)
        assertEquals(3_000L, vm.state.value.totalCents)
        assertEquals(Person.MARINA, vm.trends.value.report!!.person)
        assertEquals(3_000L, vm.trends.value.report!!.totalCents)
        assertEquals(1, vm.habits.value.report!!.tiles.expenseCount)
        assertEquals(3_000L, vm.habits.value.report!!.tiles.medianCents)
        // "Tko plaća" keeps both people's payments.
        assertEquals(1_000L, vm.trends.value.report!!.payers.paid.getValue(Person.LOVEL).last().cents)

        vm.setPerson(null)
        assertNull(vm.trends.value.report!!.person)
        assertEquals(4_000L, vm.trends.value.report!!.totalCents)
    }

    @Test
    fun theBrojkePeriodDoesNotRecomputeTrends() = runTest {
        add(1_000, d(2026, 9, 2))
        add(2_000, d(2026, 10, 2))
        val vm = viewModel()
        val before = vm.trends.value.report
        assertNotNull(before)
        vm.previous() // Brojke: September
        vm.setMode(StatsMode.YEAR)
        assertSame(before, vm.trends.value.report)
        assertEquals(AnalyticsSelection(), vm.analyticsSelection.value)
    }

    @Test
    fun newDataRecomputesBothTabs() = runTest {
        add(1_000, d(2026, 10, 2))
        val vm = viewModel()
        assertEquals(1_000L, vm.trends.value.report!!.totalCents)
        add(500, d(2026, 10, 5), description = "Konzum")
        assertEquals(1_500L, vm.trends.value.report!!.totalCents)
        assertEquals("Konzum", vm.habits.value.report!!.topDescriptions.single().label)
    }

    @Test
    fun theRepositoryIsListenedToOnce() = runTest {
        var subscriptions = 0
        val counting = object : ExpenseRepository by expenses {
            override fun observeAll(): Flow<List<Expense>> = flow {
                subscriptions++
                emitAll(expenses.observeAll())
            }
        }
        viewModel(counting)
        assertEquals(1, subscriptions)
    }

    @Test
    fun loadErrorIsReported() = runTest {
        val failing = object : ExpenseRepository by expenses {
            override fun observeAll(): Flow<List<Expense>> = flow { throw IllegalStateException("denied") }
        }
        val vm = viewModel(failing)
        assertTrue(vm.trends.value.error)
        assertTrue(vm.habits.value.error)
        assertFalse(vm.trends.value.empty)
    }
}
