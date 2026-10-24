package hr.ml.expensetracker.ui.home

import hr.ml.expensetracker.domain.Person
import hr.ml.expensetracker.domain.RecurringTemplate
import hr.ml.expensetracker.domain.SplitType
import hr.ml.expensetracker.session.CurrentPerson
import hr.ml.expensetracker.testing.InMemoryExpenseRepository
import hr.ml.expensetracker.testing.InMemoryRecurringRepository
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
class DueViewModelTest {
    private val now = Instant.parse("2026-10-24T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneId.of("Europe/Zagreb"))
    private val created = Instant.parse("2026-01-10T10:00:00Z")
    private val expenses = InMemoryExpenseRepository(clock = clock::instant)
    private val recurring = InMemoryRecurringRepository(clock = clock::instant)

    private val rent = RecurringTemplate(
        id = "t1",
        name = "Stanarina",
        category = "Stanarina",
        description = "",
        amountCents = 50_000,
        paidBy = Person.MARINA,
        split = SplitType.HALF,
        dayOfMonth = 15,
        active = true,
        skippedPeriods = setOf("2026-09"),
        createdAt = created,
        updatedAt = created,
        createdBy = Person.LOVEL,
    )

    private fun TestScope.viewModel(person: Person? = Person.LOVEL): DueViewModel {
        val vm = DueViewModel(recurring, expenses, CurrentPerson { person }, clock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.due.collect {} }
        return vm
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun listsDueAndOverdueTemplates() = runTest {
        recurring.add(rent)
        recurring.add(rent.copy(id = "t2", name = "Internet", dayOfMonth = 28, skippedPeriods = emptySet(), amountCents = 3_000))
        val vm = viewModel()
        val due = vm.due.value
        // Internet: September overdue (28 September passed), October not yet (the 28th is ahead).
        // Stanarina: September skipped, October due since the 15th.
        assertEquals(
            listOf("t2" to YearMonth.of(2026, 9), "t1" to YearMonth.of(2026, 10)),
            due.map { it.template.id to it.period },
        )
        assertTrue(due[0].overdue)
        assertFalse(due[1].overdue)
    }

    @Test
    fun postingWritesTheDeterministicExpenseAndTheCardGoes() = runTest {
        recurring.add(rent)
        val vm = viewModel(Person.MARINA)
        val item = vm.due.value.single()

        assertTrue(vm.post(item))
        val e = expenses.records.value.values.single()
        assertEquals("rec-t1-2026-10", e.id)
        assertEquals(LocalDate.of(2026, 10, 15), e.date)
        assertEquals("Stanarina", e.category)
        assertEquals(50_000L, e.amountCents)
        assertEquals(Person.MARINA, e.paidBy)
        assertEquals(SplitType.HALF, e.split)
        assertEquals(Person.MARINA, e.createdBy)
        assertEquals(now, e.createdAt)
        assertEquals("t1", e.templateId)
        assertEquals("2026-10", e.period)
        assertTrue(vm.due.value.isEmpty())
    }

    @Test
    fun postingTwiceWritesOneDocument() = runTest {
        recurring.add(rent)
        val item = viewModel(Person.LOVEL).due.value.single()
        // Both phones post the same month: the same id, so still one expense.
        DueViewModel(recurring, expenses, CurrentPerson { Person.LOVEL }, clock).post(item)
        DueViewModel(recurring, expenses, CurrentPerson { Person.MARINA }, Clock.offset(clock, java.time.Duration.ofMinutes(1))).post(item)
        val e = expenses.records.value.values.single()
        // The second write keeps the stored authorship (the rules never let it change).
        assertEquals(Person.LOVEL, e.createdBy)
        assertEquals(now, e.createdAt)
    }

    @Test
    fun aDeletedPostingIsDueAgainAndRepostingKeepsTheAuthorship() = runTest {
        recurring.add(rent)
        val vm = viewModel(Person.LOVEL)
        val item = vm.due.value.single()
        vm.post(item)
        expenses.softDelete(item.expenseId)
        assertEquals(listOf(item), vm.due.value)

        val other = viewModel(Person.MARINA)
        assertTrue(other.post(other.due.value.single()))
        val e = expenses.records.value.getValue(item.expenseId)
        assertFalse(e.deleted)
        assertEquals(Person.LOVEL, e.createdBy)
        assertTrue(vm.due.value.isEmpty())
    }

    @Test
    fun postingNeedsASignedInPerson() = runTest {
        recurring.add(rent)
        val vm = viewModel(person = null)
        assertFalse(vm.post(vm.due.value.single()))
        assertTrue(expenses.records.value.isEmpty())
    }

    @Test
    fun skipAndUndo() = runTest {
        recurring.add(rent)
        val vm = viewModel()
        val item = vm.due.value.single()

        vm.skip(item)
        assertTrue(vm.due.value.isEmpty())
        val skipped = recurring.records.value.getValue("t1")
        assertEquals(setOf("2026-09", "2026-10"), skipped.skippedPeriods)
        assertEquals(now, skipped.updatedAt)
        assertEquals(created, skipped.createdAt)
        assertTrue(expenses.records.value.isEmpty())

        vm.undoSkip(item)
        assertEquals(setOf("2026-09"), recurring.records.value.getValue("t1").skippedPeriods)
        assertEquals(listOf(item.copy(template = recurring.records.value.getValue("t1"))), vm.due.value)
    }

    @Test
    fun undoKeepsOtherChangesMadeMeanwhile() = runTest {
        recurring.add(rent)
        val vm = viewModel()
        val item = vm.due.value.single()
        vm.skip(item)
        // The other phone renames the template while the snackbar is up.
        recurring.update(recurring.records.value.getValue("t1").copy(name = "Najam"))
        vm.undoSkip(item)
        val t = recurring.records.value.getValue("t1")
        assertEquals("Najam", t.name)
        assertEquals(setOf("2026-09"), t.skippedPeriods)
    }

    @Test
    fun inactiveTemplatesAreNotDue() = runTest {
        recurring.add(rent.copy(active = false))
        assertTrue(viewModel().due.value.isEmpty())
    }
}
