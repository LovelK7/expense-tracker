package hr.ml.expensetracker.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
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
            TroskoviTheme(dynamicColor = false) { AppShell(person = Person.LOVEL, onSignOut = {}) }
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
}
