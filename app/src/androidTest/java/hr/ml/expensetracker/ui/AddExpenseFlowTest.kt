package hr.ml.expensetracker.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import hr.ml.expensetracker.domain.Categories
import hr.ml.expensetracker.domain.Expense
import hr.ml.expensetracker.domain.ExpenseRepository
import hr.ml.expensetracker.domain.Money
import hr.ml.expensetracker.domain.Person
import hr.ml.expensetracker.domain.SettlementRepository
import hr.ml.expensetracker.domain.SplitType
import hr.ml.expensetracker.session.CurrentPerson
import hr.ml.expensetracker.testing.InMemoryExpenseRepository
import hr.ml.expensetracker.testing.InMemorySettlementRepository
import hr.ml.expensetracker.ui.expense.ExpenseFormTags
import hr.ml.expensetracker.ui.theme.TroskoviTheme
import java.time.Instant
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.dsl.module

/** Add-expense happy path through the real shell, with in-memory repositories instead of Firestore. */
@RunWith(AndroidJUnit4::class)
class AddExpenseFlowTest {
    @get:Rule
    val compose = createComposeRule()

    private val expenses = InMemoryExpenseRepository()
    private val settlements = InMemorySettlementRepository()

    // Overrides the Firestore-backed definitions of the app module (Koin allows overrides).
    private val fakes = module {
        single<ExpenseRepository> { expenses }
        single<SettlementRepository> { settlements }
        single<CurrentPerson> { CurrentPerson { Person.LOVEL } }
    }

    @Before
    fun setUp() = loadKoinModules(fakes)

    @After
    fun tearDown() = unloadKoinModules(fakes)

    private fun euros(cents: Long) = Money.format(cents)

    @Test
    fun addAnExpenseInThreeTaps() {
        compose.setContent {
            TroskoviTheme { AppShell(person = Person.LOVEL, onSignOut = {}) }
        }
        compose.onNodeWithText("Nema troškova").assertIsDisplayed()

        compose.onNodeWithText("Dodaj").performClick()
        compose.onNodeWithTag(ExpenseFormTags.SAVE).assertIsNotEnabled()
        compose.onNodeWithTag(ExpenseFormTags.AMOUNT).performTextInput("12,5")
        compose.onNodeWithText("Hrana").performClick()
        compose.onNodeWithTag(ExpenseFormTags.SAVE).assertIsEnabled()
        compose.onNodeWithText("Spremi ${euros(1250)}").assertIsDisplayed()
        compose.onNodeWithTag(ExpenseFormTags.SAVE).performClick()

        // Back on Home: snackbar, the new row and the balance (Lovel paid, shared: Marina owes 6,25 €).
        compose.onNodeWithText("Trošak spremljen").assertIsDisplayed()
        compose.onNodeWithText("Danas").assertIsDisplayed()
        compose.onNodeWithText(euros(1250)).assertIsDisplayed()
        compose.onNodeWithText("Marina duguje Lovelu ${euros(625)}").assertIsDisplayed()
        compose.onNodeWithText("Platio Lovel · Podjela").assertIsDisplayed()

        val saved = expenses.records.value.values.single()
        assertEquals(1250L, saved.amountCents)
        assertEquals("Hrana", saved.category)
        assertEquals(Person.LOVEL, saved.paidBy)
        assertEquals(SplitType.HALF, saved.split)
        assertEquals(Person.LOVEL, saved.createdBy)
        assertEquals(LocalDate.now(), saved.date)
    }

    private fun chipLabels(): List<String> =
        compose.onAllNodesWithTag(ExpenseFormTags.CATEGORY_CHIP).fetchSemanticsNodes().map { node ->
            node.config[SemanticsProperties.Text].joinToString("") { it.text }
        }

    @Test
    fun categoryChipsAreOrderedByUsage() {
        val at = Instant.now()
        fun used(category: String, times: Int) = List(times) {
            Expense("$category-$it", LocalDate.now(), category, "", 100, Person.MARINA, SplitType.HALF, at, at, Person.MARINA)
        }
        expenses.records.value = (used("Putovanja", 3) + used("Režije", 2) + used("Prijenos", 5)).associateBy { it.id }
        compose.setContent {
            TroskoviTheme { AppShell(person = Person.LOVEL, onSignOut = {}) }
        }
        compose.onNodeWithText("Dodaj").performClick()

        // Used categories first, then the static order; "Prijenos" is never offered for a new expense.
        val labels = chipLabels()
        assertEquals(listOf("Putovanja", "Režije", "Hrana", "Prijevoz", "Hrana van"), labels.take(5))
        assertFalse("Prijenos" in labels)
        assertEquals(Categories.FOR_PICKER.size, labels.size)
    }

    @Test
    fun withoutExpensesTheChipsUseTheStaticOrder() {
        compose.setContent {
            TroskoviTheme { AppShell(person = Person.LOVEL, onSignOut = {}) }
        }
        compose.onNodeWithText("Dodaj").performClick()
        assertEquals(Categories.FOR_PICKER, chipLabels())
    }
}
