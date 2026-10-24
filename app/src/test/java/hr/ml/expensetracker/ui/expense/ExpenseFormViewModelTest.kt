package hr.ml.expensetracker.ui.expense

import hr.ml.expensetracker.domain.Categories
import hr.ml.expensetracker.domain.Expense
import hr.ml.expensetracker.domain.ExpenseRepository
import hr.ml.expensetracker.domain.Person
import hr.ml.expensetracker.domain.SplitType
import hr.ml.expensetracker.session.CurrentPerson
import hr.ml.expensetracker.testing.InMemoryExpenseRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExpenseFormViewModelTest {
    private val now = Instant.parse("2026-10-24T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneId.of("Europe/Zagreb"))
    private val today = LocalDate.of(2026, 10, 24)
    private val repo = InMemoryExpenseRepository(clock = clock::instant)

    private fun viewModel(person: Person? = Person.MARINA) =
        ExpenseFormViewModel(repo, CurrentPerson { person }, clock)

    private val stored get() = repo.records.value.values.toList()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun parsesCommaAndDotAmounts() {
        val vm = viewModel()
        vm.onAmountChange("12,5")
        assertEquals(1250L, vm.state.value.amountCents)
        vm.onAmountChange("12.50")
        assertEquals("12,50", vm.state.value.amountText) // a dot is shown as a comma
        assertEquals(1250L, vm.state.value.amountCents)
        vm.onAmountChange("7")
        assertEquals(700L, vm.state.value.amountCents)
    }

    @Test
    fun ignoresKeystrokesThatAreNotAnAmount() {
        val vm = viewModel()
        vm.onAmountChange("12,5")
        vm.onAmountChange("12,555") // a third decimal
        vm.onAmountChange("12,5a")
        vm.onAmountChange("-12")
        assertEquals("12,5", vm.state.value.amountText)
    }

    @Test
    fun saveNeedsAPositiveAmountAndACategory() {
        val vm = viewModel()
        assertFalse(vm.state.value.canSave)
        vm.onAmountChange("12,5")
        assertFalse(vm.state.value.canSave) // no category yet
        vm.onCategorySelect("Hrana")
        assertTrue(vm.state.value.canSave)
        vm.onAmountChange("0")
        assertFalse(vm.state.value.canSave)
        vm.onAmountChange("")
        assertFalse(vm.state.value.canSave)

        assertFalse(vm.save())
        assertTrue(stored.isEmpty())
    }

    @Test
    fun defaultsToTheSignedInPayerHalfSplitAndToday() {
        val s = viewModel(Person.MARINA).state.value
        assertEquals(Person.MARINA, s.paidBy)
        assertEquals(SplitType.HALF, s.split)
        assertEquals(today, s.date)
        assertNull(s.category)
        assertEquals(Categories.FOR_PICKER, s.categories)
    }

    @Test
    fun savesANewExpenseCreatedByTheSignedInPerson() {
        val vm = viewModel(Person.MARINA)
        vm.onAmountChange("12,5")
        vm.onCategorySelect("Hrana")
        vm.onDescriptionChange("  Konzum  ")
        vm.onPaidByChange(Person.LOVEL)
        vm.onSplitChange(SplitType.FULL_FOR_OTHER)
        vm.onDateChange(today.minusDays(2))

        assertTrue(vm.save())

        val e = stored.single()
        assertEquals(1250L, e.amountCents)
        assertEquals("Hrana", e.category)
        assertEquals("Konzum", e.description)
        assertEquals(Person.LOVEL, e.paidBy)
        assertEquals(SplitType.FULL_FOR_OTHER, e.split)
        assertEquals(today.minusDays(2), e.date)
        assertEquals(Person.MARINA, e.createdBy)
        assertEquals(now, e.createdAt)
        assertEquals(now, e.updatedAt)
        assertFalse(e.deleted)
        // The form is ready for the next entry.
        assertEquals("", vm.state.value.amountText)
        assertNull(vm.state.value.category)
    }

    @Test
    fun doesNotSaveWithoutASignedInPerson() {
        val vm = viewModel(person = null)
        vm.onAmountChange("5")
        vm.onCategorySelect("Hrana")
        assertFalse(vm.save())
        assertTrue(stored.isEmpty())
    }

    @Test
    fun editKeepsIdAuthorshipAndCreationTime() {
        val createdAt = Instant.parse("2026-10-01T08:00:00Z")
        val original = Expense(
            id = "abc",
            date = LocalDate.of(2026, 10, 1),
            category = "Prijevoz",
            description = "Gorivo",
            amountCents = 4_000,
            paidBy = Person.LOVEL,
            split = SplitType.HALF,
            createdAt = createdAt,
            updatedAt = createdAt,
            createdBy = Person.LOVEL,
        )
        repo.add(original)
        val vm = viewModel(Person.MARINA) // Marina edits Lovel's expense

        vm.startEdit("abc")
        val loaded = vm.state.value
        assertTrue(loaded.loaded)
        assertEquals("40,00", loaded.amountText)
        assertEquals("Prijevoz", loaded.category)
        assertEquals("Gorivo", loaded.description)
        assertEquals(Person.LOVEL, loaded.paidBy)
        assertEquals(LocalDate.of(2026, 10, 1), loaded.date)

        vm.onAmountChange("45,5")
        vm.onCategorySelect("Putovanja")
        assertTrue(vm.save())

        val e = stored.single()
        assertEquals("abc", e.id)
        assertEquals(Person.LOVEL, e.createdBy)
        assertEquals(createdAt, e.createdAt)
        assertEquals(now, e.updatedAt)
        assertEquals(4_550L, e.amountCents)
        assertEquals("Putovanja", e.category)
        assertNull(vm.state.value.editingId)
    }

    @Test
    fun editingALegacyCategoryKeepsItSelectable() {
        val at = Instant.parse("2025-01-01T00:00:00Z")
        repo.add(Expense("old", LocalDate.of(2025, 1, 1), "Prijenos", "", 100, Person.LOVEL, SplitType.FULL_FOR_OTHER, at, at, Person.LOVEL))
        val vm = viewModel()
        vm.startEdit("old")
        assertEquals("Prijenos", vm.state.value.categories.first())
        assertEquals("Prijenos", vm.state.value.category)
    }

    private fun addUses(category: String, times: Int, deleted: Boolean = false) = repeat(times) {
        val at = Instant.parse("2025-01-01T00:00:00Z")
        repo.add(Expense(repo.newId(), LocalDate.of(2025, 1, 1), category, "", 100, Person.LOVEL, SplitType.HALF, at, at, Person.LOVEL, deleted))
    }

    @Test
    fun usageCountsOrderTheCategories() {
        addUses("Zabava", 3)
        addUses("Poklon", 2)
        addUses("Hrana", 5, deleted = true) // deleted rows do not count
        addUses("Prijenos", 9) // legacy transfers never become a new-expense chip
        val s = viewModel().state.value
        assertEquals(listOf("Zabava", "Poklon") + (Categories.FOR_PICKER - setOf("Zabava", "Poklon")), s.categories)
    }

    @Test
    fun withoutExpensesTheStaticOrderIsUsed() {
        assertEquals(Categories.FOR_PICKER, viewModel().state.value.categories)
    }

    @Test
    fun whenExpensesCannotBeLoadedTheStaticOrderIsUsed() {
        val failing = object : ExpenseRepository by repo {
            override fun observeAll(): Flow<List<Expense>> = flow { throw IllegalStateException("offline") }
        }
        val vm = ExpenseFormViewModel(failing, CurrentPerson { Person.LOVEL }, clock)
        assertEquals(Categories.FOR_PICKER, vm.state.value.categories)
    }

    @Test
    fun theOrderFollowsNewExpensesAndAPristineFormStaysPristine() {
        val vm = viewModel()
        addUses("Usluge", 1)
        assertEquals("Usluge", vm.state.value.categories.first())

        // A form showing the newer order is still untouched: resetIfPristine keeps it as is.
        vm.resetIfPristine()
        assertEquals("Usluge", vm.state.value.categories.first())

        // Saving counts too: two "Zdravlje" make it the first chip of the next blank form.
        repeat(2) {
            vm.onAmountChange("5")
            vm.onCategorySelect("Zdravlje")
            assertTrue(vm.save())
        }
        assertEquals(listOf("Zdravlje", "Usluge"), vm.state.value.categories.take(2))
        assertNull(vm.state.value.category)
    }

    @Test
    fun editingUsesTheUsageOrderToo() {
        addUses("Ostalo", 2)
        val at = Instant.parse("2025-01-01T00:00:00Z")
        repo.add(Expense("old", LocalDate.of(2025, 1, 1), "Prijenos", "", 100, Person.LOVEL, SplitType.FULL_FOR_OTHER, at, at, Person.LOVEL))
        val vm = viewModel()
        vm.startEdit("old")
        assertEquals(listOf("Prijenos", "Ostalo"), vm.state.value.categories.take(2))
    }

    @Test
    fun editingAMissingExpenseReportsIt() {
        val vm = viewModel()
        vm.startEdit("nope")
        assertTrue(vm.state.value.missing)
        assertFalse(vm.state.value.canSave)
    }
}
