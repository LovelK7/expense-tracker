package hr.ml.expensetracker.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeViewModelTest {
    @Test
    fun sampleDataGivesTheLegacyBalance() {
        val state = HomeViewModel(SampleData.expenses, SampleData.settlements).state.value
        // -500.00 + 42.70 - 31.00 + 25.00 - 71.40: Marina owes Lovel 534,70 €
        assertEquals(-53_470L, state.balanceCents)
        assertEquals(5, state.expenseCount)
    }
}
