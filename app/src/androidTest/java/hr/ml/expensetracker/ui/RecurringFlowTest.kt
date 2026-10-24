package hr.ml.expensetracker.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import hr.ml.expensetracker.domain.ExpenseRepository
import hr.ml.expensetracker.domain.Money
import hr.ml.expensetracker.domain.Person
import hr.ml.expensetracker.domain.RecurringRepository
import hr.ml.expensetracker.domain.SettlementRepository
import hr.ml.expensetracker.domain.SplitType
import hr.ml.expensetracker.session.CurrentPerson
import hr.ml.expensetracker.testing.InMemoryExpenseRepository
import hr.ml.expensetracker.testing.InMemoryRecurringRepository
import hr.ml.expensetracker.testing.InMemorySettlementRepository
import hr.ml.expensetracker.ui.common.FormTags
import hr.ml.expensetracker.ui.expense.ExpenseFormTags
import hr.ml.expensetracker.ui.recurring.TemplatesTags
import hr.ml.expensetracker.ui.theme.TroskoviTheme
import java.time.LocalDate
import java.time.YearMonth
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.dsl.module

/** Stage 10 through the real shell: create a template, post it from its due card, prefill from its chip. */
@RunWith(AndroidJUnit4::class)
class RecurringFlowTest {
    @get:Rule
    val compose = createComposeRule()

    private val expenses = InMemoryExpenseRepository()
    private val recurring = InMemoryRecurringRepository()

    private val fakes = module {
        single<ExpenseRepository> { expenses }
        single<SettlementRepository> { InMemorySettlementRepository() }
        single<RecurringRepository> { recurring }
        single<CurrentPerson> { CurrentPerson { Person.LOVEL } }
    }

    @Before
    fun setUp() = loadKoinModules(fakes)

    @After
    fun tearDown() = unloadKoinModules(fakes)

    @Test
    fun createATemplatePostItFromTheDueCardAndPrefillTheForm() {
        compose.setContent {
            TroskoviTheme { AppShell(person = Person.LOVEL, onSignOut = {}) }
        }
        // No templates yet: the Dodaj tab has no "Predlošci" row.
        compose.onNodeWithText("Dodaj").performClick()
        compose.onNodeWithTag(ExpenseFormTags.TEMPLATE_CHIPS).assertDoesNotExist()
        compose.onNodeWithText("Početna").performClick()

        // Predlošci from the top bar: a new template, due on the 1st (so it is due today).
        compose.onNodeWithTag(ShellTags.TEMPLATES).performClick()
        compose.onNodeWithTag(TemplatesTags.NEW).performClick()
        compose.onNodeWithTag(TemplatesTags.NAME).performTextInput("Stanarina")
        compose.onNodeWithTag(TemplatesTags.AMOUNT).performTextInput("500")
        // The name field says "Stanarina" too: pick the chip by its tag.
        compose.onNode(hasTestTag(FormTags.CATEGORY_CHIP) and hasText("Stanarina")).performScrollTo().performClick()
        compose.onNodeWithTag(FormTags.CATEGORY_SELECTED).assertTextContains("Stanarina")
        compose.onNodeWithText("Marina").performScrollTo().performClick()
        compose.onNodeWithTag(TemplatesTags.DAY).performScrollTo().performTextClearance()
        compose.onNodeWithTag(TemplatesTags.DAY).performTextInput("1")
        compose.onNodeWithTag(TemplatesTags.SAVE).performClick()
        compose.onNodeWithText("Predložak spremljen").assertIsDisplayed()
        compose.onNodeWithTag(TemplatesTags.row("template-1")).assertIsDisplayed()

        val template = recurring.records.value.values.single()
        assertEquals("Stanarina", template.name)
        assertEquals(50_000L, template.amountCents)
        assertEquals(Person.MARINA, template.paidBy)
        assertEquals(SplitType.HALF, template.split)
        assertEquals(1, template.dayOfMonth)

        // Back on Početna: the due card for this month.
        compose.onNodeWithTag(TemplatesTags.CLOSE).performClick()
        val month = YearMonth.now()
        val expenseId = "rec-template-1-$month"
        val card = "due_card_$expenseId"
        compose.onNodeWithTag(card).assertIsDisplayed()
        compose.onNodeWithText("Dospjelo").assertIsDisplayed()
        compose.onNodeWithText("Stanarina · ", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("due_add_$expenseId").performClick()

        compose.onNodeWithText("Trošak dodan").assertIsDisplayed()
        compose.onNodeWithTag(card).assertDoesNotExist()
        val posted = expenses.records.value.getValue(expenseId)
        assertEquals("template-1", posted.templateId)
        assertEquals(month.toString(), posted.period)
        assertEquals(month.atDay(1), posted.date)
        assertEquals(Person.LOVEL, posted.createdBy)
        compose.onNodeWithText("Platila Marina · Podjela").assertIsDisplayed()
        compose.onNodeWithText("Lovel duguje Marini ${Money.format(25_000)}").assertIsDisplayed()

        // The Dodaj tab offers it as a chip that prefills an ordinary expense.
        compose.onNodeWithText("Dodaj").performClick()
        compose.onNodeWithTag(ExpenseFormTags.templateChip("template-1")).performClick()
        compose.onNodeWithTag(ExpenseFormTags.AMOUNT).assertTextContains("500,00")
        compose.onNodeWithTag(ExpenseFormTags.CATEGORY_SELECTED).assertTextContains("Stanarina")
        compose.onNodeWithTag(ExpenseFormTags.SAVE).performClick()
        val manual = expenses.records.value.values.single { it.id != expenseId }
        assertNull(manual.templateId)
        assertEquals(LocalDate.now(), manual.date)
        assertEquals(Person.MARINA, manual.paidBy)
    }
}
