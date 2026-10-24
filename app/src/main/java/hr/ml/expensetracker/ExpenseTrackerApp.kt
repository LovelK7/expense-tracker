package hr.ml.expensetracker

import android.app.Application
import hr.ml.expensetracker.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class ExpenseTrackerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@ExpenseTrackerApp)
            modules(appModule)
        }
    }
}
