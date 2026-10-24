package hr.ml.expensetracker.ui.stats

import hr.ml.expensetracker.domain.Expense
import hr.ml.expensetracker.domain.ExpenseRepository
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
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
class StatisticsViewModelTest {
    private val now = Instant.parse("2026-10-24T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneId.of("Europe/Zagreb"))
    private val october = YearMonth.of(2026, 10)
    private val expenses = InMemoryExpenseRepository(clock = clock::instant)
    private var seq = 0

    private fun add(
        cents: Long,
        date: LocalDate,
        category: String = "Hrana",
        paidBy: Person = Person.LOVEL,
        split: SplitType = SplitType.HALF,
        description: String = "",
        createdAt: Instant = now.minusSeconds(3_600),
    ): Expense {
        val e = Expense("e${++seq}", date, category, description, cents, paidBy, split, createdAt, createdAt, paidBy)
        expenses.add(e)
        return e
    }

    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    private fun TestScope.viewModel(repo: ExpenseRepository = expenses): StatisticsViewModel {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val vm = StatisticsViewModel(repo, clock, computeDispatcher = dispatcher)
        backgroundScope.launch(dispatcher) { vm.state.collect {} }
        return vm
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun loadingUntilTheFirstSnapshot() = runTest {
        val never = object : ExpenseRepository by expenses {
            override fun observeAll(): Flow<List<Expense>> = MutableSharedFlow()
        }
        val vm = viewModel(never)
        assertTrue(vm.state.value.loading)
        assertFalse(vm.state.value.empty)
        assertEquals(StatsSelection(StatsMode.MONTH, october), vm.state.value.selection)
    }

    @Test
    fun emptyStateWithoutExpensesInThePeriod() = runTest {
        add(1_000, d(2026, 9, 30))
        val vm = viewModel()
        val s = vm.state.value
        assertFalse(s.loading)
        assertTrue(s.empty)
        assertEquals(0L, s.totalCents)
        assertTrue(s.categories.isEmpty())
        // The September expense is reachable with the arrow.
        assertTrue(s.canGoPrevious)
        assertFalse(s.canGoNext)
    }

    @Test
    fun loadErrorIsReported() = runTest {
        val failing = object : ExpenseRepository by expenses {
            override fun observeAll(): Flow<List<Expense>> = flow { throw IllegalStateException("denied") }
        }
        val s = viewModel(failing).state.value
        assertTrue(s.error)
        assertFalse(s.loading)
        assertFalse(s.empty)
    }

    @Test
    fun monthBoundariesUseTheExpenseDate() = runTest {
        add(100, d(2026, 9, 30))
        add(200, d(2026, 10, 1))
        add(300, d(2026, 10, 31))
        add(400, d(2026, 11, 1))
        val s = viewModel().state.value
        assertEquals(500L, s.totalCents)
        assertEquals(2, s.expenseCount)
        assertTrue(s.months.isEmpty()) // Mjesec shows the per-person shares instead
    }

    @Test
    fun yearBoundariesAndMonthRowsNewestFirstWithGapsFilled() = runTest {
        add(100, d(2025, 12, 31))
        add(1_000, d(2026, 1, 1))
        add(3_000, d(2026, 3, 15))
        add(2_000, d(2026, 12, 31))
        add(50, d(2027, 1, 1))
        val vm = viewModel()
        vm.setMode(StatsMode.YEAR)
        val s = vm.state.value
        assertEquals(StatsMode.YEAR, s.mode)
        assertEquals(2026, s.selection.year)
        assertEquals(6_000L, s.totalCents)
        assertEquals((12 downTo 1).map { YearMonth.of(2026, it) }, s.months.map { it.month })
        assertEquals(2_000L, s.months.first().cents)
        assertEquals(0L, s.months[1].cents) // November: no expenses, still a row
        assertEquals(1f, s.months.single { it.month.monthValue == 3 }.barFraction)
        assertEquals(2f / 3, s.months.first().barFraction, 1e-6f)
        assertFalse(s.olderMonthsHidden)
    }

    @Test
    fun allTimeShowsTheLast24MonthsAndFlagsOlderOnes() = runTest {
        add(700, d(2023, 1, 10))
        add(100, d(2024, 11, 3))
        add(200, d(2026, 10, 2), category = "Režije")
        val vm = viewModel()
        vm.setMode(StatsMode.ALL)
        val s = vm.state.value
        assertEquals(1_000L, s.totalCents)
        assertEquals(3, s.expenseCount)
        assertEquals(YearMonth.of(2023, 1), s.allFrom)
        assertEquals(october, s.allTo)
        assertEquals(24, s.months.size)
        assertEquals(october, s.months.first().month)
        assertEquals(YearMonth.of(2024, 11), s.months.last().month)
        assertTrue(s.olderMonthsHidden)
        assertFalse(s.canGoPrevious)
        assertFalse(s.canGoNext)
        // Categories still cover all time, not just the months shown.
        assertEquals(listOf("Hrana" to 800L, "Režije" to 200L), s.categories.map { it.category to it.cents })
    }

    @Test
    fun allTimeWithFewMonthsHidesNothing() = runTest {
        add(100, d(2026, 8, 3))
        add(100, d(2026, 10, 3))
        val vm = viewModel()
        vm.setMode(StatsMode.ALL)
        val s = vm.state.value
        assertEquals(listOf(10, 9, 8).map { YearMonth.of(2026, it) }, s.months.map { it.month })
        assertFalse(s.olderMonthsHidden)
    }

    @Test
    fun allTimeWithoutExpensesIsEmpty() = runTest {
        val vm = viewModel()
        vm.setMode(StatsMode.ALL)
        assertTrue(vm.state.value.empty)
    }

    @Test
    fun categoriesLargestFirstWithPercentagesAndBars() = runTest {
        add(1_000, d(2026, 10, 1), "Hrana")
        add(500, d(2026, 10, 2), "Hrana")
        add(4_500, d(2026, 10, 3), "Režije")
        add(1_500, d(2026, 10, 4), "Prijevoz")
        add(2_500, d(2026, 10, 5), "Adaptacija")
        val s = viewModel().state.value
        assertEquals(10_000L, s.totalCents)
        assertEquals(listOf("Režije", "Adaptacija", "Hrana", "Prijevoz"), s.categories.map { it.category })
        assertEquals(listOf(45.0, 25.0, 15.0, 15.0), s.categories.map { it.percent })
        listOf(1f, 2_500f / 4_500, 1_500f / 4_500, 1_500f / 4_500).zip(s.categories) { expected, row ->
            assertEquals(expected, row.barFraction, 1e-6f)
        }
        assertEquals(100.0, s.categories.sumOf { it.percent }, 1e-9)
    }

    @Test
    fun perPersonSharesAndPayments() = runTest {
        add(1_001, d(2026, 10, 1), paidBy = Person.LOVEL) // shared
        add(3_000, d(2026, 10, 2), paidBy = Person.MARINA) // shared
        add(2_000, d(2026, 10, 3), paidBy = Person.MARINA, split = SplitType.FULL_FOR_OTHER) // for Lovel
        val s = viewModel().state.value
        val lovel = s.people.single { it.person == Person.LOVEL }
        val marina = s.people.single { it.person == Person.MARINA }
        // HALF total 4001: Lovel 2000 (2000.5 half-even), Marina 2001; plus Marina's 2000 loan to Lovel.
        assertEquals(4_000L, lovel.shareCents)
        assertEquals(2_001L, marina.shareCents)
        assertEquals(1_001L, lovel.paidCents)
        assertEquals(5_000L, marina.paidCents)
        assertEquals(s.totalCents, lovel.shareCents + marina.shareCents)
        assertEquals(4_000f / 6_001, lovel.shareFraction, 1e-6f)
        assertEquals(listOf(Person.LOVEL, Person.MARINA), s.people.map { it.person })
    }

    @Test
    fun deletedExpensesAreIgnored() = runTest {
        val gone = add(9_999, d(2026, 10, 5))
        add(100, d(2026, 10, 6))
        expenses.softDelete(gone.id)
        val s = viewModel().state.value
        assertEquals(100L, s.totalCents)
        assertEquals(1, s.expenseCount)
    }

    @Test
    fun monthNavigationStaysWithinTheData() = runTest {
        add(100, d(2026, 8, 15))
        add(200, d(2026, 12, 1)) // dated ahead of today
        val vm = viewModel()
        assertTrue(vm.state.value.canGoPrevious)
        assertTrue(vm.state.value.canGoNext)

        vm.previous()
        assertEquals(YearMonth.of(2026, 9), vm.state.value.selection.month)
        assertTrue(vm.state.value.empty)
        vm.previous()
        assertEquals(YearMonth.of(2026, 8), vm.state.value.selection.month)
        assertEquals(100L, vm.state.value.totalCents)
        assertFalse(vm.state.value.canGoPrevious)
        vm.previous() // no-op at the earliest month
        assertEquals(YearMonth.of(2026, 8), vm.state.value.selection.month)

        repeat(10) { vm.next() }
        assertEquals(YearMonth.of(2026, 12), vm.state.value.selection.month)
        assertEquals(200L, vm.state.value.totalCents)
        assertFalse(vm.state.value.canGoNext)
    }

    @Test
    fun monthNavigationCrossesYears() = runTest {
        add(100, d(2025, 12, 20))
        add(200, d(2026, 1, 20))
        val vm = viewModel()
        repeat(10) { vm.previous() }
        assertEquals(YearMonth.of(2025, 12), vm.state.value.selection.month)
        assertEquals(2025, vm.state.value.selection.year)
        vm.next()
        assertEquals(YearMonth.of(2026, 1), vm.state.value.selection.month)
        assertEquals(200L, vm.state.value.totalCents)
    }

    @Test
    fun yearNavigationAndSwitchingBackToMonth() = runTest {
        add(100, d(2024, 5, 1))
        add(200, d(2026, 10, 1))
        val vm = viewModel()
        vm.setMode(StatsMode.YEAR)
        assertFalse(vm.state.value.canGoNext)
        vm.previous()
        vm.previous()
        assertEquals(2024, vm.state.value.selection.year)
        assertEquals(100L, vm.state.value.totalCents)
        assertFalse(vm.state.value.canGoPrevious)

        // Back to Mjesec in a past year: its last month.
        vm.setMode(StatsMode.MONTH)
        assertEquals(YearMonth.of(2024, 12), vm.state.value.selection.month)
        // Mjesec -> Godina keeps the month's year.
        vm.setMode(StatsMode.YEAR)
        assertEquals(2024, vm.state.value.selection.year)
        vm.next()
        vm.next()
        vm.setMode(StatsMode.MONTH)
        assertEquals(october, vm.state.value.selection.month)
    }

    @Test
    fun expandingACategoryListsItsExpensesNewestFirst() = runTest {
        val t = now.minusSeconds(86_400)
        val older = add(100, d(2026, 10, 1), "Hrana", description = "Konzum")
        val morning = add(200, d(2026, 10, 20), "Hrana", description = "Pekara", createdAt = t)
        val evening = add(300, d(2026, 10, 20), "Hrana", paidBy = Person.MARINA, createdAt = t.plusSeconds(3_600))
        add(400, d(2026, 9, 20), "Hrana") // other month
        add(500, d(2026, 10, 2), "Režije")
        val vm = viewModel()
        assertTrue(vm.state.value.categories.none { it.expanded || it.expenses.isNotEmpty() })

        vm.toggleCategory("Hrana")
        val hrana = vm.state.value.categories.single { it.category == "Hrana" }
        assertTrue(hrana.expanded)
        assertEquals(listOf(evening, morning, older), hrana.expenses)
        assertFalse(vm.state.value.categories.single { it.category == "Režije" }.expanded)

        // The expansion follows the period.
        vm.previous()
        assertEquals(listOf(400L), vm.state.value.categories.single { it.category == "Hrana" }.expenses.map { it.amountCents })

        vm.toggleCategory("Hrana")
        assertTrue(vm.state.value.categories.single().expenses.isEmpty())
    }

    @Test
    fun updatesWhenTheRepositoryChanges() = runTest {
        val vm = viewModel()
        assertTrue(vm.state.value.empty)
        add(1_250, d(2026, 10, 24))
        assertEquals(1_250L, vm.state.value.totalCents)
        assertEquals(listOf("Hrana"), vm.state.value.categories.map { it.category })
    }

    @Test
    fun thousandsOfRows() = runTest {
        val categories = listOf("Hrana", "Režije", "Prijevoz", "Putovanja", "MPO")
        expenses.records.value = (0 until 5_000).associate { i ->
            val e = Expense(
                "bulk$i", d(2023, 1, 1).plusDays(i % 1_400L), categories[i % 5], "", 100L + i,
                if (i % 2 == 0) Person.LOVEL else Person.MARINA, SplitType.HALF, now, now, Person.LOVEL,
            )
            e.id to e
        }
        val vm = viewModel()
        vm.setMode(StatsMode.ALL)
        val s = vm.state.value
        assertEquals(5_000, s.expenseCount)
        assertEquals((0 until 5_000).sumOf { 100L + it }, s.totalCents)
        assertEquals(5, s.categories.size)
        assertEquals(24, s.months.size)
        assertTrue(s.olderMonthsHidden)
    }
}
