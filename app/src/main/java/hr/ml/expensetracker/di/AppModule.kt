package hr.ml.expensetracker.di

import hr.ml.expensetracker.ui.home.HomeViewModel
import hr.ml.expensetracker.ui.home.SampleData
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    // Stage 2 replaces the sample data with the Firestore repository.
    viewModel { HomeViewModel(SampleData.expenses, SampleData.settlements) }
}
