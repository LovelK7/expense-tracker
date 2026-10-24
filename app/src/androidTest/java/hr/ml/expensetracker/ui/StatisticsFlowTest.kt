package hr.ml.expensetracker.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import hr.ml.expensetracker.domain.Categories
import hr.ml.expensetracker.domain.Expense
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
import hr.ml.expensetracker.ui.stats.StatsTags
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

/** "Statistika" through the real shell: switching periods and expanding a category. */
@RunWith(AndroidJUnit4::class)
class StatisticsFlowTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.now()
    private val lastMonth = today.minusMonths(1)
    private val at = Instant.now()

    private fun expense(id: String, date: LocalDate, category: String, cents: Long, description: String = "", paidBy: Person = Person.LOVEL) =
        Expense(id, date, category, description, cents, paidBy, SplitType.HALF, at, at, paidBy)

    private val expenses = InMemoryExpenseRepository(
        listOf(
            expense("1", today, "Hrana", 3_000, "Konzum"),
            expense("t", today, Categories.TRANSFER, 50_000, "Prijenos Marini"), // not spending
            expense("2", today, "Režije", 7_000, "Struja", Person.MARINA),
            expense("3", lastMonth, "Putovanja", 12_345, "Trajekt"),
        ),
    )

    private val fakes = module {
        single<ExpenseRepository> { expenses }
        single<SettlementRepository> { InMemorySettlementRepository() }
        single<RecurringRepository> { InMemoryRecurringRepository() }
        single<CurrentPerson> { CurrentPerson { Person.LOVEL } }
    }

    private val months = listOf(
        "siječanj", "veljača", "ožujak", "travanj", "svibanj", "lipanj",
        "srpanj", "kolovoz", "rujan", "listopad", "studeni", "prosinac",
    )

    private fun label(month: YearMonth) = "${months[month.monthValue - 1]} ${month.year}."

    @Before
    fun setUp() = loadKoinModules(fakes)

    @After
    fun tearDown() = unloadKoinModules(fakes)

    @Test
    fun switchPeriodsAndExpandACategory() {
        compose.setContent {
            TroskoviTheme { AppShell(person = Person.LOVEL, onSignOut = {}) }
        }
        compose.onNodeWithText("Statistika").performClick()

        // Mjesec (the current one): this month's categories, largest first, and the shares.
        compose.onNodeWithTag(StatsTags.PERIOD_LABEL).assertTextEquals(label(YearMonth.from(today)))
        compose.onNodeWithTag(StatsTags.TOTAL).assertTextEquals(Money.format(10_000))
        compose.onNodeWithTag(StatsTags.category("Režije")).assertIsDisplayed()
        compose.onNodeWithTag(StatsTags.category("Hrana")).assertIsDisplayed()
        compose.onNodeWithText("70,0 %").assertIsDisplayed()
        compose.onNodeWithText("Udio po osobi").assertIsDisplayed()
        compose.onNodeWithTag(StatsTags.NEXT).assertIsNotEnabled()

        // Expanding a category lists its expenses.
        compose.onNodeWithText("Struja").assertDoesNotExist()
        compose.onNodeWithTag(StatsTags.category("Režije")).performClick()
        compose.onNodeWithTag(StatsTags.trend("Režije")).assertExists()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Struja"))
        compose.onNodeWithText("Struja").assertIsDisplayed()
        compose.onNodeWithText("Platila Marina", substring = true).assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag(StatsTags.PREVIOUS))

        // Previous month: only the trip.
        compose.onNodeWithTag(StatsTags.PREVIOUS).performClick()
        compose.onNodeWithTag(StatsTags.PERIOD_LABEL).assertTextEquals(label(YearMonth.from(lastMonth)))
        compose.onNodeWithTag(StatsTags.category("Putovanja")).assertIsDisplayed()
        compose.onNodeWithTag(StatsTags.category("Hrana")).assertDoesNotExist()
        compose.onNodeWithTag(StatsTags.PREVIOUS).assertIsNotEnabled()

        // Sve: everything, with the monthly trend instead of the shares.
        compose.onNodeWithText("Sve").performClick()
        compose.onNodeWithTag(StatsTags.TOTAL).assertTextEquals(Money.format(22_345))
        compose.onNodeWithTag(StatsTags.category("Putovanja")).assertIsDisplayed()
        // Režije is still expanded (its trend and expense sit above Hrana): scroll down.
        compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag(StatsTags.category("Hrana")))
        compose.onNodeWithTag(StatsTags.category("Hrana")).assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Po godinama"))
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Po mjesecima"))
        compose.onNodeWithText("Po mjesecima").assertIsDisplayed()

        // Godina: the year of the month last shown.
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Godina"))
        compose.onNodeWithText("Godina").performClick()
        compose.onNodeWithTag(StatsTags.PERIOD_LABEL).assertTextEquals("${lastMonth.year}.")
        compose.onNodeWithTag(StatsTags.category("Putovanja")).assertIsDisplayed()
    }

    @Test
    fun personViewComparisonAndTransfers() {
        compose.setContent {
            TroskoviTheme { AppShell(person = Person.LOVEL, onSignOut = {}) }
        }
        compose.onNodeWithText("Statistika").performClick()

        // This month (10000) against last month (12345): -19 %; nothing a year ago, so one line only.
        compose.onNodeWithTag(StatsTags.COMPARISON)
            .assertTextEquals("−19 % u odnosu na ${label(YearMonth.from(lastMonth))}")
        compose.onNodeWithTag(StatsTags.TRANSFERS).assertTextEquals("Prijenosi (isključeni): ${Money.format(50_000)}")
        compose.onNodeWithText("Udio").assertIsDisplayed()

        // Lovel: what he paid, how many, his part of the couple's spending; no share table.
        compose.onNodeWithTag(StatsTags.person(Person.LOVEL)).performClick()
        compose.onNodeWithTag(StatsTags.TOTAL).assertTextEquals(Money.format(3_000))
        compose.onNodeWithTag(StatsTags.PERSON_SUMMARY).assertTextEquals("1 trošak · 30 % ukupne potrošnje")
        compose.onNodeWithText("Udio").assertDoesNotExist()
        compose.onNodeWithText("Udio po osobi").assertDoesNotExist()
        compose.onNodeWithTag(StatsTags.category("Režije")).assertDoesNotExist()
        compose.onNodeWithTag(StatsTags.COMPARISON).assertTextContains("−76 %", substring = true) // 3000 vs 12345

        // Expanding his category lists his single expenses, with the 12-month trend above them.
        compose.onNodeWithTag(StatsTags.category("Hrana")).performClick()
        compose.onNodeWithTag(StatsTags.trend("Hrana")).assertExists()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Konzum"))
        compose.onNodeWithText("Konzum").assertIsDisplayed()
        compose.onNodeWithText("Podjela", substring = true).assertIsDisplayed()

        // The selection survives a tab switch.
        compose.onNodeWithText("Početna").performClick()
        compose.onNodeWithText("Statistika").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag(StatsTags.person(Person.LOVEL)))
        compose.onNodeWithTag(StatsTags.person(Person.LOVEL)).assertIsSelected()
        compose.onNodeWithTag(StatsTags.TOTAL).assertTextEquals(Money.format(3_000))

        // Sve for both: the years, newest first.
        compose.onNodeWithText("Sve").performClick()
        compose.onNodeWithTag(StatsTags.person(null)).performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Po godinama"))
        compose.onNodeWithTag(StatsTags.year(today.year)).assertExists()
    }

    @Test
    fun emptyPeriod() {
        expenses.records.value = emptyMap()
        compose.setContent {
            TroskoviTheme { AppShell(person = Person.LOVEL, onSignOut = {}) }
        }
        compose.onNodeWithText("Statistika").performClick()
        compose.onNodeWithText("Nema troškova u ovom razdoblju").assertIsDisplayed()
    }
}
