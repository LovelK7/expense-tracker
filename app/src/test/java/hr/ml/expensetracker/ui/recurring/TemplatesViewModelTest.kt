package hr.ml.expensetracker.ui.recurring

import hr.ml.expensetracker.domain.Expense
import hr.ml.expensetracker.domain.Person
import hr.ml.expensetracker.domain.RecurringTemplate
import hr.ml.expensetracker.domain.SplitType
import hr.ml.expensetracker.session.CurrentPerson
import hr.ml.expensetracker.testing.InMemoryExpenseRepository
import hr.ml.expensetracker.testing.InMemoryRecurringRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TemplatesViewModelTest {
    private val now = Instant.parse("2026-10-24T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneId.of("Europe/Zagreb"))
    private val earlier = Instant.parse("2026-01-10T10:00:00Z")
    private val expenses = InMemoryExpenseRepository(clock = clock::instant)
    private val recurring = InMemoryRecurringRepository(clock = clock::instant)

    private fun TestScope.viewModel(person: Person = Person.MARINA): TemplatesViewModel {
        val vm = TemplatesViewModel(recurring, expenses, CurrentPerson { person }, clock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.templates.collect {} }
        return vm
    }

    private val stored get() = recurring.records.value.values.toList()

    private fun rent(id: String = "t1", name: String = "Stanarina") = RecurringTemplate(
        id, name, "Stanarina", "", 50_000, Person.MARINA, SplitType.HALF, 15, true, setOf("2026-09"),
        earlier, earlier, Person.LOVEL,
    )

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun createsATemplate() = runTest {
        val vm = viewModel(Person.MARINA)
        vm.openNew()
        val blank = vm.form.value!!
        assertEquals(Person.MARINA, blank.paidBy)
        assertEquals("24", blank.dayText) // today's day by default
        assertTrue(blank.categoriesExpanded)
        assertFalse(blank.canSave)

        vm.onNameChange("Stanarina")
        vm.onAmountChange("500")
        vm.onCategorySelect("Stanarina")
        assertFalse(vm.form.value!!.categoriesExpanded) // collapsed to the chosen chip
        vm.onDescriptionChange(" Najam stana ")
        vm.onDayChange("15")
        vm.onSplitChange(SplitType.FULL_FOR_OTHER)
        assertTrue(vm.form.value!!.canSave)
        assertTrue(vm.save())

        assertNull(vm.form.value) // back to the list
        val t = stored.single()
        assertEquals("Stanarina", t.name)
        assertEquals("Stanarina", t.category)
        assertEquals("Najam stana", t.description)
        assertEquals(50_000L, t.amountCents)
        assertEquals(15, t.dayOfMonth)
        assertEquals(SplitType.FULL_FOR_OTHER, t.split)
        assertTrue(t.active)
        assertEquals(emptySet<String>(), t.skippedPeriods)
        assertEquals(Person.MARINA, t.createdBy)
        assertEquals(now, t.createdAt)
        assertEquals(listOf(t), vm.templates.value)
    }

    @Test
    fun theDayMustBeOneToThirtyOne() = runTest {
        val vm = viewModel()
        vm.openNew()
        vm.onNameChange("Vrtić")
        vm.onAmountChange("120")
        vm.onCategorySelect("MPO")
        for ((text, valid) in listOf("0" to false, "32" to false, "" to false, "1" to true, "31" to true)) {
            vm.onDayChange(text)
            assertEquals(text, valid, vm.form.value!!.canSave)
        }
        vm.onDayChange("123") // more than two digits: ignored
        vm.onDayChange("1a")
        assertEquals("31", vm.form.value!!.dayText)
        vm.onNameChange("   ")
        assertFalse(vm.form.value!!.canSave)
        assertFalse(vm.save())
        assertTrue(stored.isEmpty())
    }

    @Test
    fun editsKeepingAuthorshipAndSkippedMonths() = runTest {
        recurring.add(rent())
        val vm = viewModel(Person.MARINA)
        vm.openEdit(vm.templates.value!!.single())
        val form = vm.form.value!!
        assertEquals("t1", form.editingId)
        assertEquals("500,00", form.amountText)
        assertEquals("15", form.dayText)
        assertFalse(form.categoriesExpanded)

        // Meanwhile the other phone skips a month.
        recurring.update(rent().copy(skippedPeriods = setOf("2026-09", "2026-10")))
        vm.onAmountChange("520")
        vm.onDayChange("1")
        vm.onActiveChange(false)
        assertTrue(vm.save())

        val t = stored.single()
        assertEquals(52_000L, t.amountCents)
        assertEquals(1, t.dayOfMonth)
        assertFalse(t.active)
        assertEquals(setOf("2026-09", "2026-10"), t.skippedPeriods)
        assertEquals(Person.LOVEL, t.createdBy)
        assertEquals(earlier, t.createdAt)
        assertEquals(now, t.updatedAt)
    }

    @Test
    fun theListSwitchTogglesActive() = runTest {
        recurring.add(rent())
        val vm = viewModel()
        vm.setActive(vm.templates.value!!.single(), false)
        assertFalse(stored.single().active)
        vm.setActive(vm.templates.value!!.single(), true)
        assertTrue(stored.single().active)
    }

    @Test
    fun deleteIsSoftAndCanBeUndone() = runTest {
        recurring.add(rent())
        recurring.add(rent("t2", "Internet"))
        val vm = viewModel()
        assertEquals(listOf("Internet", "Stanarina"), vm.templates.value!!.map { it.name }) // by name

        vm.openEdit(vm.templates.value!!.first { it.id == "t1" })
        val deleted = vm.deleteEditing()
        assertNotNull(deleted)
        assertNull(vm.form.value)
        assertTrue(recurring.records.value.getValue("t1").deleted) // still stored
        assertEquals(listOf("t2"), vm.templates.value!!.map { it.id })

        vm.undoDelete(deleted!!)
        assertFalse(recurring.records.value.getValue("t1").deleted)
        assertEquals(earlier, recurring.records.value.getValue("t1").createdAt)
        assertEquals(listOf("t2", "t1"), vm.templates.value!!.map { it.id })
    }

    @Test
    fun saveAsTemplatePrefillsFromAnExpense() = runTest {
        val at = Instant.parse("2026-10-03T10:00:00Z")
        expenses.add(Expense("e1", LocalDate.of(2026, 10, 3), "Režije", " Struja HEP ", 6_250, Person.LOVEL, SplitType.FULL_FOR_OTHER, at, at, Person.LOVEL))
        expenses.add(Expense("e2", LocalDate.of(2026, 10, 31), "Stanarina", "", 50_000, Person.MARINA, SplitType.HALF, at, at, Person.MARINA))
        val vm = viewModel(Person.MARINA)

        vm.openFromExpense("e1")
        val form = vm.form.value!!
        assertEquals("Struja HEP", form.name)
        assertEquals("Režije", form.category)
        assertEquals("Struja HEP", form.description)
        assertEquals("62,50", form.amountText)
        assertEquals(Person.LOVEL, form.paidBy)
        assertEquals(SplitType.FULL_FOR_OTHER, form.split)
        assertEquals("3", form.dayText)
        assertTrue(form.fromExpense)
        assertFalse(form.categoriesExpanded)

        // Without a description the name is the category; dismissing closes the whole screen.
        vm.openFromExpense("e2")
        assertEquals("Stanarina", vm.form.value!!.name)
        assertEquals("31", vm.form.value!!.dayText)
        assertTrue(vm.closeForm())
        assertNull(vm.form.value)

        vm.openFromExpense("e2")
        assertTrue(vm.save())
        assertEquals(Person.MARINA, stored.single().createdBy)
        assertEquals(31, stored.single().dayOfMonth)
    }

    @Test
    fun closingAnOrdinaryFormReturnsToTheList() = runTest {
        val vm = viewModel()
        vm.openNew()
        assertFalse(vm.closeForm())
        assertNull(vm.form.value)
    }
}
