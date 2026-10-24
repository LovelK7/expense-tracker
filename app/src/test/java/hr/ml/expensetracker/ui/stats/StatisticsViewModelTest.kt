package hr.ml.expensetracker.ui.stats

import hr.ml.expensetracker.domain.Categories
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
    fun transfersAreExcludedAndReportedOnTheSide() = runTest {
        add(100_000, d(2026, 10, 3), Categories.TRANSFER, split = SplitType.FULL_FOR_OTHER)
        add(2_000, d(2026, 10, 4), "Hrana")
        add(50_000, d(2026, 10, 5), "Stanarina", paidBy = Person.MARINA) // rent is ordinary spending
        val vm = viewModel()
        val s = vm.state.value
        assertEquals(52_000L, s.totalCents)
        assertEquals(2, s.expenseCount)
        assertEquals(100_000L, s.transferCents)
        assertEquals(listOf("Stanarina", "Hrana"), s.categories.map { it.category })
        assertEquals(s.totalCents, s.people.sumOf { it.shareCents })
        assertEquals(s.totalCents, s.people.sumOf { it.paidCents })

        // Marina paid no transfer: no line in her view.
        vm.setPerson(Person.MARINA)
        assertEquals(0L, vm.state.value.transferCents)
        vm.setPerson(Person.LOVEL)
        assertEquals(100_000L, vm.state.value.transferCents)
        assertEquals(2_000L, vm.state.value.totalCents)
    }

    @Test
    fun aPeriodWithOnlyTransfersIsEmptyButShowsThem() = runTest {
        add(5_000, d(2026, 10, 3), Categories.TRANSFER, split = SplitType.FULL_FOR_OTHER)
        val s = viewModel().state.value
        assertTrue(s.empty)
        assertEquals(5_000L, s.transferCents)
    }

    @Test
    fun personViewCoversOnlyWhatThatPersonPaid() = runTest {
        val konzum = add(3_000, d(2026, 10, 1), "Hrana", Person.LOVEL, description = "Konzum")
        add(1_000, d(2026, 10, 2), "Hrana", Person.MARINA)
        val gorivo = add(2_000, d(2026, 10, 3), "Prijevoz", Person.LOVEL, SplitType.FULL_FOR_OTHER)
        add(4_000, d(2026, 10, 4), "Režije", Person.MARINA)
        val vm = viewModel()
        assertEquals(null, vm.state.value.person)
        assertEquals(2, vm.state.value.people.size)
        assertEquals(null, vm.state.value.personPercent)

        vm.setPerson(Person.LOVEL)
        val s = vm.state.value
        assertEquals(Person.LOVEL, s.person)
        assertEquals(5_000L, s.totalCents)
        assertEquals(2, s.expenseCount)
        assertEquals(50, s.personPercent) // 5000 of 10000
        assertTrue(s.people.isEmpty()) // no "Udio" in the person view
        assertEquals(listOf("Hrana" to 3_000L, "Prijevoz" to 2_000L), s.categories.map { it.category to it.cents })
        assertEquals(listOf(60.0, 40.0), s.categories.map { it.percent })

        vm.toggleCategory("Hrana")
        vm.toggleCategory("Prijevoz")
        assertEquals(listOf(konzum), vm.state.value.categories.single { it.category == "Hrana" }.expenses)
        assertEquals(listOf(gorivo), vm.state.value.categories.single { it.category == "Prijevoz" }.expenses)

        vm.setPerson(Person.MARINA)
        assertEquals(5_000L, vm.state.value.totalCents)
        assertEquals(listOf("Režije", "Hrana"), vm.state.value.categories.map { it.category })
        assertEquals(1_000L, vm.state.value.categories.single { it.category == "Hrana" }.expenses.single().amountCents)

        vm.setPerson(null)
        assertEquals(10_000L, vm.state.value.totalCents)
        assertEquals(2, vm.state.value.people.size)
    }

    @Test
    fun personPercentIsRoundedHalfEven() = runTest {
        add(1, d(2026, 10, 1), paidBy = Person.LOVEL)
        add(7, d(2026, 10, 2), paidBy = Person.MARINA)
        val vm = viewModel()
        vm.setPerson(Person.LOVEL)
        assertEquals(12, vm.state.value.personPercent) // 12.5 -> 12
        vm.setPerson(Person.MARINA)
        assertEquals(88, vm.state.value.personPercent) // 87.5 -> 88
    }

    @Test
    fun personSelectionSurvivesModeSwitchesAndNavigation() = runTest {
        add(1_000, d(2025, 6, 1), paidBy = Person.MARINA)
        add(2_000, d(2026, 10, 1), paidBy = Person.MARINA)
        add(9_000, d(2026, 10, 2), paidBy = Person.LOVEL)
        val vm = viewModel()
        vm.setPerson(Person.MARINA)
        vm.toggleCategory("Hrana")
        vm.setMode(StatsMode.YEAR)
        assertEquals(Person.MARINA, vm.state.value.person)
        assertEquals(2_000L, vm.state.value.totalCents)
        vm.previous()
        assertEquals(1_000L, vm.state.value.totalCents)
        vm.setMode(StatsMode.ALL)
        assertEquals(Person.MARINA, vm.state.value.person)
        assertEquals(3_000L, vm.state.value.totalCents)
        assertTrue(vm.state.value.categories.single().expanded)
        vm.setMode(StatsMode.MONTH)
        assertEquals(Person.MARINA, vm.state.value.selection.person)
        assertEquals(YearMonth.of(2025, 12), vm.state.value.selection.month)
    }

    @Test
    fun oneStatePerChange() = runTest {
        add(1_000, d(2026, 10, 1), paidBy = Person.MARINA)
        add(2_000, d(2026, 10, 2), paidBy = Person.LOVEL)
        val vm = viewModel()
        val seen = mutableListOf<StatsUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { seen += it } }
        val before = seen.size
        vm.setPerson(Person.LOVEL)
        assertEquals(before + 1, seen.size)
        vm.toggleCategory("Hrana")
        assertEquals(before + 2, seen.size)
        vm.setMode(StatsMode.YEAR)
        assertEquals(before + 3, seen.size)
    }

    @Test
    fun monthComparesWithThePreviousMonthAndTheSameMonthAYearEarlier() = runTest {
        add(10_000, d(2025, 10, 10))
        add(20_000, d(2026, 9, 10))
        add(22_400, d(2026, 10, 10))
        add(77_777, d(2026, 9, 11), Categories.TRANSFER) // not part of September's spending
        val vm = viewModel()
        assertEquals(
            listOf(
                ComparisonRow(12, ComparisonReference.Month(YearMonth.of(2026, 9))),
                ComparisonRow(124, ComparisonReference.Month(YearMonth.of(2025, 10))),
            ),
            vm.state.value.comparisons,
        )
        // September 2026: August has no data, September 2025 neither: no lines.
        vm.previous()
        assertEquals(emptyList<ComparisonRow>(), vm.state.value.comparisons)
    }

    @Test
    fun comparisonsFollowThePersonFilter() = runTest {
        add(10_000, d(2026, 9, 10), paidBy = Person.LOVEL)
        add(9_500, d(2026, 10, 10), paidBy = Person.LOVEL)
        add(5_000, d(2026, 10, 11), paidBy = Person.MARINA)
        val vm = viewModel()
        assertEquals(listOf(ComparisonRow(45, ComparisonReference.Month(YearMonth.of(2026, 9)))), vm.state.value.comparisons)
        vm.setPerson(Person.LOVEL)
        assertEquals(listOf(ComparisonRow(-5, ComparisonReference.Month(YearMonth.of(2026, 9)))), vm.state.value.comparisons)
        vm.setPerson(Person.MARINA)
        assertEquals(emptyList<ComparisonRow>(), vm.state.value.comparisons) // Marina paid nothing in September
    }

    @Test
    fun theCurrentYearIsComparedYearToDate() = runTest {
        add(1_000, d(2024, 5, 1))
        add(1_500, d(2025, 3, 1))
        add(9_000, d(2025, 11, 1)) // after 24 October: not in the year-to-date reference
        add(1_800, d(2026, 2, 1))
        val vm = viewModel()
        vm.setMode(StatsMode.YEAR)
        assertEquals(listOf(ComparisonRow(20, ComparisonReference.Year(2025, toDate = true))), vm.state.value.comparisons)
        vm.previous()
        // 2025 in full against 2024 in full: 10500 vs 1000.
        assertEquals(listOf(ComparisonRow(950, ComparisonReference.Year(2024, toDate = false))), vm.state.value.comparisons)
        vm.previous()
        assertEquals(emptyList<ComparisonRow>(), vm.state.value.comparisons) // nothing in 2023
        vm.setMode(StatsMode.ALL)
        assertEquals(emptyList<ComparisonRow>(), vm.state.value.comparisons)
    }

    @Test
    fun anExpandedCategoryShowsItsTwelveMonthTrend() = runTest {
        // Hrana: 1000 a month from November 2024 to October 2026; Režije only in March 2026.
        (0L until 24L).forEach { add(1_000, d(2024, 11, 5).plusMonths(it), "Hrana") }
        add(6_000, d(2026, 3, 1), "Režije")
        add(3_000, d(2026, 10, 6), "Hrana", Person.MARINA)
        val vm = viewModel()
        assertEquals(null, vm.state.value.categories.single { it.category == "Hrana" }.trend)
        vm.toggleCategory("Hrana")
        vm.toggleCategory("Režije")

        val hrana = vm.state.value.categories.single { it.category == "Hrana" }.trend!!
        assertEquals((0L until 12L).map { YearMonth.of(2025, 11).plusMonths(it) }, hrana.bars.map { it.month })
        assertEquals(listOf(october), hrana.bars.filter { it.inPeriod }.map { it.month })
        assertEquals(4_000L, hrana.bars.last().cents)
        assertEquals(1f, hrana.bars.last().fraction)
        assertEquals(0.25f, hrana.bars.first().fraction, 1e-6f)
        assertEquals(1_250L, hrana.monthlyAverageCents) // 15000 / 12

        // Režije has no October spending, so no row; it stays expanded and shows up in March.
        assertEquals(null, vm.state.value.categories.firstOrNull { it.category == "Režije" })
        repeat(7) { vm.previous() }
        assertEquals(YearMonth.of(2026, 3), vm.state.value.selection.month)
        val march = vm.state.value.categories.single { it.category == "Režije" }.trend!!
        assertEquals(YearMonth.of(2026, 3), march.bars.last().month)
        assertEquals(500L, march.monthlyAverageCents) // 6000 / 12

        // Person view: Marina's Hrana, averaged from her first month (October 2026).
        vm.setMode(StatsMode.YEAR)
        vm.setPerson(Person.MARINA)
        val marina = vm.state.value.categories.single { it.category == "Hrana" }.trend!!
        assertEquals(october, marina.bars.last().month) // Godina of the current year: ends today
        assertEquals(3_000L, marina.monthlyAverageCents)
        assertEquals((1..10).map { YearMonth.of(2026, it) }, marina.bars.filter { it.inPeriod }.map { it.month })
    }

    @Test
    fun trendWindowsOfPastYearsAndAllTime() = runTest {
        add(1_200, d(2024, 1, 5))
        add(2_400, d(2024, 12, 5))
        add(100, d(2026, 10, 5))
        val vm = viewModel()
        vm.setMode(StatsMode.YEAR)
        vm.previous()
        vm.previous()
        vm.toggleCategory("Hrana")
        val y2024 = vm.state.value.categories.single().trend!!
        assertEquals((1..12).map { YearMonth.of(2024, it) }, y2024.bars.map { it.month })
        assertTrue(y2024.bars.all { it.inPeriod })
        assertEquals(300L, y2024.monthlyAverageCents)

        vm.setMode(StatsMode.ALL)
        val all = vm.state.value.categories.single().trend!!
        assertEquals(october, all.bars.last().month)
        assertEquals(YearMonth.of(2025, 11), all.bars.first().month)
        assertTrue(all.bars.all { it.inPeriod })
    }

    @Test
    fun allTimeListsTheYearsNewestFirstWithMonthlyAverages() = runTest {
        add(12_000, d(2023, 1, 10))
        add(6_000, d(2024, 7, 1))
        add(10_000, d(2026, 5, 1), paidBy = Person.MARINA)
        add(99_999, d(2025, 5, 1), Categories.TRANSFER)
        val vm = viewModel()
        assertTrue(vm.state.value.years.isEmpty()) // Mjesec: no year rows
        vm.setMode(StatsMode.ALL)
        val years = vm.state.value.years
        assertEquals(listOf(2026, 2025, 2024, 2023), years.map { it.year })
        assertEquals(listOf(10_000L, 0L, 6_000L, 12_000L), years.map { it.cents })
        assertEquals(listOf(1_000L, 0L, 500L, 1_000L), years.map { it.monthlyAverageCents }) // 2026: 10 months
        listOf(10_000f / 12_000, 0f, 0.5f, 1f).zip(years) { expected, row -> assertEquals(expected, row.barFraction, 1e-6f) }
        assertTrue(vm.state.value.months.isNotEmpty()) // "Po mjesecima" stays below

        vm.setPerson(Person.MARINA)
        assertEquals(listOf(2026), vm.state.value.years.map { it.year })
        vm.setMode(StatsMode.YEAR)
        assertTrue(vm.state.value.years.isEmpty())
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
