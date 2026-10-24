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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelActionsTest {
    private val now = Instant.parse("2026-10-24T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneId.of("Europe/Zagreb"))
    private val today = LocalDate.of(2026, 10, 24)
    private val expenses = InMemoryExpenseRepository(clock = clock::instant)
    private val settlements = InMemorySettlementRepository(clock = clock::instant)

    private fun expense(
        id: String,
        cents: Long,
        paidBy: Person,
        split: SplitType = SplitType.HALF,
        date: LocalDate = today,
        createdAt: Instant = now.minusSeconds(3_600),
    ) = Expense(id, date, "Hrana", "", cents, paidBy, split, createdAt, createdAt, paidBy)

    private fun TestScope.viewModel(person: Person = Person.LOVEL): HomeViewModel {
        val vm = HomeViewModel(expenses, settlements, CurrentPerson { person }, clock, UnconfinedTestDispatcher(testScheduler))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        return vm
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun interleavesRecordsByDayNewestFirst() = runTest {
        val t = Instant.parse("2026-10-24T08:00:00Z")
        expenses.add(expense("old", 100, Person.LOVEL, date = today.minusDays(1), createdAt = t))
        expenses.add(expense("morning", 200, Person.LOVEL, createdAt = t))
        expenses.add(expense("evening", 300, Person.MARINA, createdAt = t.plusSeconds(7_200)))
        settlements.add(Settlement("s", today, Person.MARINA, Person.LOVEL, 50, "", t.plusSeconds(3_600), t, Person.MARINA))
        val vm = viewModel()

        val days = vm.state.value.days
        assertEquals(listOf(today, today.minusDays(1)), days.map { it.date })
        assertEquals(listOf("e:evening", "s:s", "e:morning"), days[0].records.map { it.key })
        assertEquals(listOf("e:old"), days[1].records.map { it.key })
        assertEquals(today, vm.state.value.today)
    }

    @Test
    fun settleUpWhenMarinaOwesLovel() = runTest {
        expenses.add(expense("1", 2_000, Person.LOVEL)) // Lovel paid 20,00 € shared: Marina owes 10,00 €
        expenses.add(expense("2", 53_000, Person.LOVEL, SplitType.FULL_FOR_OTHER))
        val vm = viewModel(Person.MARINA)
        assertEquals(-54_000L, vm.state.value.balanceCents)

        vm.openSettleUp()
        val draft = vm.state.value.settleUp!!
        assertEquals(Person.MARINA, draft.from)
        assertEquals(Person.LOVEL, draft.to)
        assertEquals("540,00", draft.amountText)
        assertEquals(today, draft.date)

        assertTrue(vm.confirmSettleUp())
        val s = settlements.records.value.values.single()
        assertEquals(Person.MARINA, s.from)
        assertEquals(Person.LOVEL, s.to)
        assertEquals(54_000L, s.amountCents)
        assertEquals(Person.MARINA, s.createdBy)
        assertEquals(now, s.createdAt)
        assertEquals(0L, vm.state.value.balanceCents)
        assertNull(vm.state.value.settleUp)
    }

    @Test
    fun settleUpWhenLovelOwesMarinaWithAnEditedAmount() = runTest {
        expenses.add(expense("1", 1_251, Person.MARINA)) // Lovel owes half of 12,51 € = 6,26 € (half-even)
        val vm = viewModel(Person.LOVEL)
        assertEquals(626L, vm.state.value.balanceCents)

        vm.openSettleUp()
        assertEquals(Person.LOVEL, vm.state.value.settleUp!!.from)
        assertEquals("6,26", vm.state.value.settleUp!!.amountText)

        vm.onSettleAmountChange("5")
        vm.onSettleNoteChange(" gotovina ")
        assertTrue(vm.confirmSettleUp())
        val s = settlements.records.value.values.single()
        assertEquals(500L, s.amountCents)
        assertEquals("gotovina", s.note)
        assertEquals(Person.LOVEL, s.createdBy)
        assertEquals(126L, vm.state.value.balanceCents)
    }

    @Test
    fun settleUpNeedsADebtAndAPositiveAmount() = runTest {
        val vm = viewModel()
        vm.openSettleUp()
        assertNull(vm.state.value.settleUp) // already settled

        expenses.add(expense("1", 1_000, Person.MARINA))
        vm.openSettleUp()
        vm.onSettleAmountChange("0")
        assertFalse(vm.state.value.settleUp!!.canConfirm)
        assertFalse(vm.confirmSettleUp())
        vm.dismissSettleUp()
        assertNull(vm.state.value.settleUp)
        assertTrue(settlements.records.value.isEmpty())
    }

    @Test
    fun undoRestoresADeletedExpense() = runTest {
        val original = expense("1", 1_250, Person.LOVEL)
        expenses.add(original)
        val vm = viewModel(Person.MARINA)
        val record = vm.state.value.days.single().records.single()
        assertEquals(-625L, vm.state.value.balanceCents)

        vm.delete(record)
        assertTrue(vm.state.value.days.isEmpty())
        assertEquals(0L, vm.state.value.balanceCents)
        assertTrue(expenses.records.value.getValue("1").deleted) // soft delete: still stored

        vm.undoDelete(record)
        val restored = expenses.records.value.getValue("1")
        assertFalse(restored.deleted)
        assertEquals(original.copy(updatedAt = now), restored)
        assertEquals(-625L, vm.state.value.balanceCents)
        assertEquals(1, vm.state.value.days.single().records.size)
    }

    @Test
    fun undoRestoresADeletedSettlement() = runTest {
        val t = Instant.parse("2026-10-20T08:00:00Z")
        settlements.add(Settlement("s", today, Person.LOVEL, Person.MARINA, 700, "", t, t, Person.LOVEL))
        val vm = viewModel()
        val record = vm.state.value.days.single().records.single()

        vm.delete(record)
        assertTrue(vm.state.value.days.isEmpty())
        vm.undoDelete(record)
        assertFalse(settlements.records.value.getValue("s").deleted)
        assertEquals(t, settlements.records.value.getValue("s").createdAt)
        assertEquals(-700L, vm.state.value.balanceCents)
    }
}
