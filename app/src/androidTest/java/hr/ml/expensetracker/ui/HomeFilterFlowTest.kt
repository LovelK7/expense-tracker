package hr.ml.expensetracker.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import hr.ml.expensetracker.domain.Expense
import hr.ml.expensetracker.domain.ExpenseRepository
import hr.ml.expensetracker.domain.Money
import hr.ml.expensetracker.domain.Person
import hr.ml.expensetracker.domain.Settlement
import hr.ml.expensetracker.domain.SettlementRepository
import hr.ml.expensetracker.domain.SplitType
import hr.ml.expensetracker.session.CurrentPerson
import hr.ml.expensetracker.testing.InMemoryExpenseRepository
import hr.ml.expensetracker.testing.InMemorySettlementRepository
import hr.ml.expensetracker.ui.home.HomeTags
import hr.ml.expensetracker.ui.theme.TroskoviTheme
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.dsl.module

/** "Početna" through the real shell: a month chip, a search, both together, clearing the search. */
@RunWith(AndroidJUnit4::class)
class HomeFilterFlowTest {
    @get:Rule
    val compose = createComposeRule()

    private val thisMonth = YearMonth.now()
    private val lastMonth = thisMonth.minusMonths(1)
    private val at = Instant.now()

    private fun expense(id: String, date: LocalDate, category: String, description: String, cents: Long) =
        Expense(id, date, category, description, cents, Person.LOVEL, SplitType.HALF, at, at, Person.LOVEL)

    private val expenses = InMemoryExpenseRepository(
        listOf(
            expense("1", thisMonth.atDay(1), "Hrana", "Spar Kastav", 3_120),
            expense("2", thisMonth.atDay(1), "Režije", "Struja", 6_000),
            expense("3", lastMonth.atDay(10), "Hrana", "Spar Rijeka", 2_000),
            expense("4", lastMonth.atDay(12), "Hrana", "Konzum", 1_500),
        ),
    )
    private val settlements = InMemorySettlementRepository(
        listOf(Settlement("s", lastMonth.atDay(15), Person.MARINA, Person.LOVEL, 9_999, "gotovina", at, at, Person.MARINA)),
    )

    private val fakes = module {
        single<ExpenseRepository> { expenses }
        single<SettlementRepository> { settlements }
        single<CurrentPerson> { CurrentPerson { Person.LOVEL } }
    }

    @Before
    fun setUp() = loadKoinModules(fakes)

    @After
    fun tearDown() = unloadKoinModules(fakes)

    private fun summary() = compose.onNodeWithTag(HomeTags.SUMMARY)

    /** The debounce runs on a real dispatcher: wait until the summary shows [text]. */
    private fun waitForSummary(text: String) {
        compose.waitUntil(timeoutMillis = 5_000) {
            runCatching { summary().assertTextEquals(text) }.isSuccess
        }
    }

    @Test
    fun pickAMonthSearchAndClear() {
        compose.setContent {
            TroskoviTheme { AppShell(person = Person.LOVEL, onSignOut = {}) }
        }
        // Default "Sve": everything, no summary line.
        compose.onNodeWithTag(HomeTags.ALL_MONTHS_CHIP).assertIsSelected()
        summary().assertDoesNotExist()
        compose.onNodeWithText("Konzum").assertIsDisplayed()

        // Last month: its expenses and the settlement; the settlement is not summed.
        compose.onNodeWithTag(HomeTags.monthChip(lastMonth)).performScrollTo().performClick()
        compose.onNodeWithTag(HomeTags.monthChip(lastMonth)).assertIsSelected()
        waitForSummary("2 troška · ${Money.format(3_500)}")
        compose.onNodeWithText("Spar Rijeka").assertIsDisplayed()
        compose.onNodeWithText("gotovina").assertIsDisplayed()
        compose.onNodeWithText("Struja").assertDoesNotExist()

        // Search within the month.
        compose.onNodeWithTag(HomeTags.SEARCH_OPEN).performClick()
        compose.onNodeWithTag(HomeTags.SEARCH_FIELD).performTextInput("spar")
        waitForSummary("1 trošak · ${Money.format(2_000)}")
        compose.onNodeWithText("Spar Rijeka").assertIsDisplayed()
        compose.onNodeWithText("Konzum").assertDoesNotExist()
        compose.onNodeWithText("gotovina").assertDoesNotExist()

        // The same search over every month.
        compose.onNodeWithTag(HomeTags.ALL_MONTHS_CHIP).performClick()
        waitForSummary("2 troška · ${Money.format(5_120)}")
        compose.onNodeWithText("Spar Kastav").assertIsDisplayed()

        // Nothing matching.
        compose.onNodeWithTag(HomeTags.SEARCH_CLEAR).performClick()
        compose.onNodeWithTag(HomeTags.SEARCH_FIELD).performTextInput("lidl")
        waitForSummary("Nema rezultata")

        // Clearing shows everything again; closing hides the field.
        compose.onNodeWithTag(HomeTags.SEARCH_CLEAR).performClick()
        compose.waitUntil(timeoutMillis = 5_000) { runCatching { summary().assertDoesNotExist() }.isSuccess }
        compose.onNodeWithText("Konzum").assertIsDisplayed()
        compose.onNodeWithTag(HomeTags.SEARCH_CLOSE).performClick()
        compose.onNodeWithTag(HomeTags.SEARCH_FIELD).assertDoesNotExist()
        compose.onNodeWithTag(HomeTags.SEARCH_OPEN).assertIsDisplayed()
    }

    @Test
    fun theMonthSurvivesATabSwitch() {
        compose.setContent {
            TroskoviTheme { AppShell(person = Person.LOVEL, onSignOut = {}) }
        }
        compose.onNodeWithTag(HomeTags.monthChip(thisMonth)).performClick()
        waitForSummary("2 troška · ${Money.format(9_120)}")

        compose.onNodeWithText("Statistika").performClick()
        compose.onNodeWithTag(HomeTags.SEARCH_OPEN).assertDoesNotExist() // search only on Početna
        compose.onNodeWithText("Početna").performClick()

        compose.onNodeWithTag(HomeTags.monthChip(thisMonth)).assertIsSelected()
        waitForSummary("2 troška · ${Money.format(9_120)}")
    }
}
