package hr.ml.expensetracker.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import hr.ml.expensetracker.R
import hr.ml.expensetracker.data.FirestoreExpenseRepository
import hr.ml.expensetracker.data.FirestoreSettlementRepository
import hr.ml.expensetracker.dev.DevEnvironment
import hr.ml.expensetracker.domain.ExpenseRepository
import hr.ml.expensetracker.domain.SettlementRepository
import hr.ml.expensetracker.session.AuthGateway
import hr.ml.expensetracker.session.CurrentPerson
import hr.ml.expensetracker.session.FirebaseAuthGateway
import hr.ml.expensetracker.session.FirestoreMembersSource
import hr.ml.expensetracker.session.GoogleSignIn
import hr.ml.expensetracker.session.MembersSource
import hr.ml.expensetracker.session.SessionRepository
import hr.ml.expensetracker.ui.expense.ExpenseFormViewModel
import hr.ml.expensetracker.ui.home.HomeViewModel
import hr.ml.expensetracker.ui.session.SessionViewModel
import hr.ml.expensetracker.ui.stats.StatisticsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

private val APP_SCOPE = named("appScope")

val appModule = module {
    single(APP_SCOPE) { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }

    // These two singles are the only places the Firebase instances are obtained, so the settings
    // (and, in debug builds with useFirebaseEmulators, the emulator hosts) land before first use.
    single { FirebaseAuth.getInstance().apply { DevEnvironment.configure(this) } }
    single {
        get<FirebaseAuth>() // Firestore asks Auth for tokens: configure Auth first.
        FirebaseFirestore.getInstance().apply {
            // Persistent cache = offline-first.
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                .build()
            DevEnvironment.configure(this)
        }
    }

    single<AuthGateway> { FirebaseAuthGateway(get(), androidContext()) }
    single<MembersSource> { FirestoreMembersSource(get()) }
    single { SessionRepository(get(), get(), get(APP_SCOPE)) }
    single { GoogleSignIn(webClientId = androidContext().getString(R.string.default_web_client_id)) }
    single<CurrentPerson> {
        val session = get<SessionRepository>()
        CurrentPerson { session.currentPerson }
    }

    single<ExpenseRepository> { FirestoreExpenseRepository(get()) }
    single<SettlementRepository> { FirestoreSettlementRepository(get()) }

    viewModel { SessionViewModel(get(), get()) }
    viewModel { HomeViewModel(get(), get(), get()) }
    viewModel { ExpenseFormViewModel(get(), get()) }
    viewModel { StatisticsViewModel(get()) }
}
