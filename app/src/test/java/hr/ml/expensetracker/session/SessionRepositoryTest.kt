package hr.ml.expensetracker.session

import hr.ml.expensetracker.domain.Members
import hr.ml.expensetracker.domain.Person
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionRepositoryTest {
    private val members = Members(
        emails = listOf("lovel.test@example.com", "marina@example.com"),
        persons = mapOf("lovel.test@example.com" to Person.LOVEL, "marina@example.com" to Person.MARINA),
    )

    private class FakeAuth : AuthGateway {
        override val user = MutableStateFlow<AuthUser?>(null)
        override suspend fun signInWithGoogleIdToken(idToken: String) = Unit
        override suspend fun signOut() {
            user.value = null
        }
    }

    private class FakeMembers(val snapshot: MutableStateFlow<MembersSnapshot>) : MembersSource {
        var subscriptions = 0
        override fun observe() = snapshot.also { subscriptions++ }
    }

    @Test
    fun resolveMapsMemberEmailToPerson() {
        assertEquals(
            SessionState.Member("lovel.test@example.com", Person.LOVEL),
            SessionState.resolve("Lovel.Test@example.com", members),
        )
        assertEquals(SessionState.Member("marina@example.com", Person.MARINA), SessionState.resolve("marina@example.com", members))
        assertEquals(SessionState.NoAccess("x@example.com"), SessionState.resolve("x@example.com", members))
        assertEquals(SessionState.NoAccess(null), SessionState.resolve(null, members))
    }

    @Test
    fun followsSignInAndMembership() = runTest {
        val auth = FakeAuth()
        val source = FakeMembers(MutableStateFlow(MembersSnapshot.Pending))
        val session = SessionRepository(auth, source, unconfinedScope())

        assertEquals(SessionState.SignedOut, session.state.value)
        assertNull(session.currentPerson)

        auth.user.value = AuthUser("uid-m", "marina@example.com")
        assertEquals(SessionState.Loading, session.state.value)

        source.snapshot.value = MembersSnapshot.Loaded(members)
        assertEquals(SessionState.Member("marina@example.com", Person.MARINA), session.state.value)
        assertEquals(Person.MARINA, session.currentPerson)

        session.signOut()
        assertEquals(SessionState.SignedOut, session.state.value)
        assertNull(session.currentPerson)
    }

    @Test
    fun nonMemberGetsNoAccess() = runTest {
        val auth = FakeAuth()
        val source = FakeMembers(MutableStateFlow(MembersSnapshot.Denied))
        val session = SessionRepository(auth, source, unconfinedScope())
        auth.user.value = AuthUser("uid-x", "stranger@example.com")
        assertEquals(SessionState.NoAccess("stranger@example.com"), session.state.value)
        assertNull(session.currentPerson)
    }

    @Test
    fun failureCanBeRetried() = runTest {
        val auth = FakeAuth()
        val source = FakeMembers(MutableStateFlow(MembersSnapshot.Failed(RuntimeException("boom"))))
        val session = SessionRepository(auth, source, unconfinedScope())
        auth.user.value = AuthUser("uid-l", "lovel.test@example.com")
        assertEquals(SessionState.Error, session.state.value)

        source.snapshot.value = MembersSnapshot.Loaded(members)
        session.retry()
        assertEquals(SessionState.Member("lovel.test@example.com", Person.LOVEL), session.state.value)
        assertEquals(2, source.subscriptions)
    }

    @Test
    fun signedOutDoesNotReadMembers() = runTest {
        val source = FakeMembers(MutableStateFlow(MembersSnapshot.Loaded(members)))
        SessionRepository(
            object : AuthGateway {
                override val user = flowOf<AuthUser?>(null)
                override suspend fun signInWithGoogleIdToken(idToken: String) = Unit
                override suspend fun signOut() = Unit
            },
            source,
            unconfinedScope(),
        )
        assertEquals(0, source.subscriptions)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun TestScope.unconfinedScope() =
    CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))
