package hr.ml.expensetracker.ui.home

import hr.ml.expensetracker.domain.Expense
import hr.ml.expensetracker.domain.Person
import hr.ml.expensetracker.domain.Settlement
import hr.ml.expensetracker.domain.SplitType
import hr.ml.expensetracker.session.CurrentPerson
import hr.ml.expensetracker.testing.InMemoryExpenseRepository
import hr.ml.expensetracker.testing.InMemorySettlementRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The month chips, the search and the summary line of "Početna". */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelFilterTest {
    private val now = Instant.parse("2026-10-24T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneId.of("Europe/Zagreb"))
    private val october = YearMonth.of(2026, 10)
    private val september = YearMonth.of(2026, 9)
    private val expenses = InMemoryExpenseRepository(clock = clock::instant)
    private val settlements = InMemorySettlementRepository(clock = clock::instant)

    private fun add(id: String, date: LocalDate, category: String, description: String, cents: Long, paidBy: Person = Person.LOVEL) {
        val at = now.minusSeconds(3_600)
        expenses.add(Expense(id, date, category, description, cents, paidBy, SplitType.HALF, at, at, paidBy))
    }

    private fun TestScope.viewModel(): HomeViewModel {
        val vm = HomeViewModel(expenses, settlements, CurrentPerson { Person.LOVEL }, clock, UnconfinedTestDispatcher(testScheduler))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        return vm
    }

    private fun HomeViewModel.shownKeys(): List<String> = state.value.days.flatMap { day -> day.records.map { it.key } }

    /** Types [text] and lets the debounce run out. */
    private fun TestScope.search(vm: HomeViewModel, text: String) {
        vm.onSearchTextChange(text)
        advanceTimeBy(SEARCH_DEBOUNCE_MS + 1)
        runCurrent()
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        add("spar", LocalDate.of(2026, 10, 20), "Hrana", "Spar Kastav", 3_120)
        add("struja", LocalDate.of(2026, 10, 1), "Režije", "Struja", 6_000, Person.MARINA)
        add("konzum", LocalDate.of(2026, 9, 30), "Hrana", "Konzum", 1_000)
        add("spar-old", LocalDate.of(2026, 9, 2), "Hrana", "SPAR", 2_000)
        add("plin", LocalDate.of(2025, 12, 31), "Režije", "Plin", 4_500)
        val t = now.minusSeconds(60)
        settlements.add(Settlement("cash", LocalDate.of(2026, 10, 5), Person.MARINA, Person.LOVEL, 10_000, "gotovina", t, t, Person.MARINA))
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun defaultShowsEverythingWithoutASummary() = runTest {
        val vm = viewModel()
        val s = vm.state.value
        assertNull(s.selectedMonth)
        assertNull(s.summary)
        assertEquals(listOf(october, september, YearMonth.of(2025, 12)), s.months)
        assertEquals(6, vm.shownKeys().size)
        assertFalse(vm.searchOpen.value)
        assertEquals("", vm.searchText)
    }

    @Test
    fun monthFilterShowsThatMonthsExpensesAndSettlements() = runTest {
        val vm = viewModel()
        val balance = vm.state.value.balanceCents

        vm.selectMonth(october)
        assertEquals(october, vm.state.value.selectedMonth)
        assertEquals(listOf("e:spar", "s:cash", "e:struja"), vm.shownKeys())
        // Settlements are listed but not summed.
        assertEquals(FilterSummary(expenseCount = 2, totalCents = 9_120, recordCount = 3), vm.state.value.summary)
        // The balance stays global.
        assertEquals(balance, vm.state.value.balanceCents)
        assertEquals(5, vm.state.value.expenseCount)

        vm.selectMonth(september)
        assertEquals(listOf("e:konzum", "e:spar-old"), vm.shownKeys())

        vm.selectMonth(null)
        assertEquals(6, vm.shownKeys().size)
        assertNull(vm.state.value.summary)
    }

    @Test
    fun searchMatchesCategoryAndDescriptionIgnoringCaseAndDiacritics() = runTest {
        val vm = viewModel()
        vm.openSearch()
        assertTrue(vm.searchOpen.value)

        search(vm, "spar")
        assertEquals(listOf("e:spar", "e:spar-old"), vm.shownKeys())
        assertEquals(FilterSummary(2, 5_120, 2), vm.state.value.summary)

        search(vm, "rezije")
        assertEquals(listOf("e:struja", "e:plin"), vm.shownKeys())

        search(vm, "hrana kastav")
        assertEquals(listOf("e:spar"), vm.shownKeys())

        search(vm, "gotovina")
        assertEquals(listOf("s:cash"), vm.shownKeys())
        assertEquals(FilterSummary(0, 0, 1), vm.state.value.summary)
    }

    @Test
    fun searchCombinesWithTheMonth() = runTest {
        val vm = viewModel()
        vm.selectMonth(september)
        search(vm, "spar")
        assertEquals(listOf("e:spar-old"), vm.shownKeys())
        assertEquals(FilterSummary(1, 2_000, 1), vm.state.value.summary)

        // Clearing the search keeps the month.
        vm.clearSearch()
        assertEquals("", vm.searchText)
        assertEquals(listOf("e:konzum", "e:spar-old"), vm.shownKeys())
        assertEquals(september, vm.state.value.selectedMonth)
    }

    @Test
    fun nothingMatchingGivesNoResults() = runTest {
        val vm = viewModel()
        search(vm, "lidl")
        assertTrue(vm.state.value.days.isEmpty())
        assertTrue(vm.state.value.summary!!.noResults)
        assertEquals(FilterSummary(0, 0, 0), vm.state.value.summary)
    }

    @Test
    fun searchIsDebouncedButClearingIsImmediate() = runTest {
        val vm = viewModel()
        vm.openSearch()
        vm.onSearchTextChange("s")
        vm.onSearchTextChange("sp")
        vm.onSearchTextChange("spa")
        // The field shows the text at once; the list waits for a pause in typing.
        assertEquals("spa", vm.searchText)
        advanceTimeBy(SEARCH_DEBOUNCE_MS - 10)
        runCurrent()
        assertEquals(6, vm.shownKeys().size)
        assertNull(vm.state.value.summary)

        vm.onSearchTextChange("spar") // restarts the wait
        advanceTimeBy(SEARCH_DEBOUNCE_MS - 10)
        runCurrent()
        assertEquals(6, vm.shownKeys().size)
        advanceTimeBy(20)
        runCurrent()
        assertEquals(listOf("e:spar", "e:spar-old"), vm.shownKeys())

        vm.closeSearch()
        assertFalse(vm.searchOpen.value)
        assertEquals("", vm.searchText)
        assertEquals(6, vm.shownKeys().size) // no wait
        assertNull(vm.state.value.summary)
    }

    @Test
    fun aSelectedMonthStaysWhenItsLastRecordIsDeleted() = runTest {
        val vm = viewModel()
        vm.selectMonth(YearMonth.of(2025, 12))
        val plin = vm.state.value.days.single().records.single()

        vm.delete(plin)
        assertTrue(vm.state.value.summary!!.noResults)
        assertEquals(YearMonth.of(2025, 12), vm.state.value.months.last()) // its chip stays to undo or leave

        vm.undoDelete(plin)
        assertEquals(listOf("e:plin"), vm.shownKeys())
        assertEquals(FilterSummary(1, 4_500, 1), vm.state.value.summary)
    }

    @Test
    fun newRecordsAppearInAFilteredList() = runTest {
        val vm = viewModel()
        vm.selectMonth(october)
        search(vm, "spar")
        add("spar-new", LocalDate.of(2026, 10, 24), "Hrana", "Spar Rijeka", 500)
        advanceUntilIdle()
        assertEquals(listOf("e:spar-new", "e:spar"), vm.shownKeys())
        assertEquals(FilterSummary(2, 3_620, 2), vm.state.value.summary)
    }
}
