package hr.ml.expensetracker.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import hr.ml.expensetracker.domain.AnalyticsRange
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
import hr.ml.expensetracker.ui.stats.StatsTab
import hr.ml.expensetracker.ui.stats.StatsTags
import hr.ml.expensetracker.ui.theme.TroskoviTheme
import java.time.Instant
import java.time.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.dsl.module

/** Statistika's Trendovi and Navike inner tabs through the real shell, with fake data. */
@RunWith(AndroidJUnit4::class)
class AnalyticsFlowTest {
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
            expense("3", lastMonth, "Putovanja", 12_345, "konzum"),
        ),
    )

    private val fakes = module {
        single<ExpenseRepository> { expenses }
        single<SettlementRepository> { InMemorySettlementRepository() }
        single<RecurringRepository> { InMemoryRecurringRepository() }
        single<CurrentPerson> { CurrentPerson { Person.LOVEL } }
    }

    @Before
    fun setUp() = loadKoinModules(fakes)

    @After
    fun tearDown() = unloadKoinModules(fakes)

    @Test
    fun trendsAndHabitsShowTheirChartsAndTiles() {
        compose.setContent {
            TroskoviTheme { AppShell(person = Person.LOVEL, onSignOut = {}) }
        }
        compose.onNodeWithText("Statistika").performClick()

        // Trendovi: 24 months by default, every chart card.
        compose.onNodeWithTag(StatsTags.tab(StatsTab.TRENDS)).performClick()
        compose.onNodeWithTag(StatsTags.range(AnalyticsRange.MONTHS_24)).assertIsSelected()
        compose.onNodeWithText("Mjesečna potrošnja").assertIsDisplayed()
        val trends = compose.onNodeWithTag(StatsTags.TRENDS_LIST)
        trends.performScrollToNode(hasTestTag(StatsTags.card("categories")))
        trends.performScrollToNode(hasText("Kategorije pojedinačno"))
        trends.performScrollToNode(hasTestTag(StatsTags.card("cumulative")))
        trends.performScrollToNode(hasText("Tko plaća"))
        compose.onNodeWithText("Tko plaća").assertIsDisplayed()

        // Navike: the tiles leave out the transfer; the range chips are shared.
        compose.onNodeWithTag(StatsTags.tab(StatsTab.HABITS)).performClick()
        compose.onNodeWithTag(StatsTags.range(AnalyticsRange.ALL)).performClick()
        compose.onNodeWithTag(StatsTags.TILES).assertIsDisplayed()
        // Median of 30,00, 70,00 and 123,45 €.
        compose.onNodeWithTag(StatsTags.tile("Medijan troška")).assertTextEquals(Money.format(7_000))
        val habits = compose.onNodeWithTag(StatsTags.HABITS_LIST)
        habits.performScrollToNode(hasTestTag(StatsTags.card("calendar")))
        habits.performScrollToNode(hasText("Raspodjela iznosa"))
        habits.performScrollToNode(hasTestTag(StatsTags.card("top")))
        // "Konzum" and "konzum" are one description.
        compose.onNodeWithText("Konzum").assertIsDisplayed()

        // Lovel only: 30,00 and 123,45 € -> 76,72 € (half-even); the selection is shared with Brojke.
        habits.performScrollToNode(hasTestTag(StatsTags.person(Person.LOVEL)))
        compose.onNodeWithTag(StatsTags.person(Person.LOVEL)).performClick()
        compose.onNodeWithTag(StatsTags.tile("Medijan troška")).assertTextEquals(Money.format(7_672))
        compose.onNodeWithTag(StatsTags.tab(StatsTab.TRENDS)).performClick()
        compose.onNodeWithTag(StatsTags.range(AnalyticsRange.ALL)).assertIsSelected()
        compose.onNodeWithTag(StatsTags.person(Person.LOVEL)).assertIsSelected()
        compose.onNodeWithTag(StatsTags.tab(StatsTab.NUMBERS)).performClick()
        compose.onNodeWithTag(StatsTags.person(Person.LOVEL)).assertIsSelected()

        // The inner tab survives switching the bottom tabs.
        compose.onNodeWithTag(StatsTags.tab(StatsTab.HABITS)).performClick()
        compose.onNodeWithText("Početna").performClick()
        compose.onNodeWithText("Statistika").performClick()
        compose.onNodeWithTag(StatsTags.tab(StatsTab.HABITS)).assertIsSelected()
        compose.onNodeWithTag(StatsTags.TILES).assertIsDisplayed()
    }

    @Test
    fun emptyHistory() {
        expenses.records.value = emptyMap()
        compose.setContent {
            TroskoviTheme { AppShell(person = Person.LOVEL, onSignOut = {}) }
        }
        compose.onNodeWithText("Statistika").performClick()
        compose.onNodeWithTag(StatsTags.tab(StatsTab.TRENDS)).performClick()
        compose.onNodeWithText("Nema troškova u ovom razdoblju").assertIsDisplayed()
        compose.onNodeWithTag(StatsTags.tab(StatsTab.HABITS)).performClick()
        compose.onNodeWithText("Nema troškova u ovom razdoblju").assertIsDisplayed()
    }
}
