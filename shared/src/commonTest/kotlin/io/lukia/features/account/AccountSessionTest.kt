package io.lukia.features.account

import io.lukia.features.account.application.AccountSession
import io.lukia.features.account.domain.AccountFailure
import io.lukia.features.account.domain.AccountProblem
import io.lukia.features.account.domain.AccountStatus
import io.lukia.features.account.domain.ServerAccount
import io.lukia.features.account.domain.StoredAccount
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class AccountSessionTest {

    private val api = FakeServerApi()
    private val store = InMemoryAccountStore()
    private var now = 1_000_000L
    private val session = AccountSession(api, store) { now }

    private val alice = ServerAccount(HomeServer, "Home shelf", "alice", isAdmin = false)

    @Test
    fun aNewInstallIsSignedOut() {
        assertEquals(AccountStatus.SignedOut, session.status.value)
    }

    @Test
    fun signingInRemembersTheAccountAndItsTokens() = runTest {
        session.signIn(HomeServer, " alice ", "correct horse")

        assertEquals(AccountStatus.SignedIn(alice), session.status.value)
        assertEquals(tokens(1), store.load()?.tokens)
    }

    @Test
    fun aRejectedPasswordLeavesTheDeviceSignedOut() = runTest {
        val failure = assertFailsWith<AccountFailure> { session.signIn(HomeServer, "alice", "wrong") }

        assertEquals(AccountProblem.SignInRejected, failure.problem)
        assertEquals(AccountStatus.SignedOut, session.status.value)
        assertNull(store.load())
    }

    @Test
    fun anAppRestartedWhileSignedInIsStillSignedIn() {
        val restarted = AccountSession(api, InMemoryAccountStore(StoredAccount(alice, tokens(1))))

        assertEquals(AccountStatus.SignedIn(alice), restarted.status.value)
    }

    @Test
    fun aTokenAboutToRunOutIsRefreshedBeforeItIsUsed() = runTest {
        session.signIn(HomeServer, "alice", "correct horse")
        store.saveTokens(store.load()!!.tokens!!.copy(accessExpiresAt = now + 30_000))

        val answer = session.authorized { _, token -> api.use(token) }

        assertEquals("ok with access-2", answer)
        assertEquals(1, api.refreshes)
    }

    @Test
    fun aTokenTheServerTurnsAwayIsRefreshedOnceAndTheRequestRetried() = runTest {
        session.signIn(HomeServer, "alice", "correct horse")
        api.validAccess.clear()

        val answer = session.authorized { _, token -> api.use(token) }

        assertEquals("ok with access-2", answer)
        assertEquals(1, api.refreshes)
    }

    @Test
    fun manyRequestsAtOnceShareOneRefresh() = runTest {
        session.signIn(HomeServer, "alice", "correct horse")
        api.validAccess.clear()

        val answers = List(5) { async { session.authorized { _, token -> api.use(token) } } }.awaitAll()

        assertEquals(List(5) { "ok with access-2" }, answers)
        assertEquals(1, api.refreshes)
    }

    @Test
    fun aRefusedRefreshAsksTheReaderToSignInAgain() = runTest {
        session.signIn(HomeServer, "alice", "correct horse")
        api.validAccess.clear()
        api.refreshRejected = true

        val failure = assertFailsWith<AccountFailure> { session.authorized { _, token -> api.use(token) } }

        assertEquals(AccountProblem.SessionExpired, failure.problem)
        assertEquals(AccountStatus.NeedsSignIn(alice), session.status.value)
        assertNull(store.load()?.tokens)
    }

    @Test
    fun anUnreachableServerDuringARefreshKeepsTheSignIn() = runTest {
        session.signIn(HomeServer, "alice", "correct horse")
        store.saveTokens(store.load()!!.tokens!!.copy(accessExpiresAt = now))
        api.reachable = false

        val failure = assertFailsWith<AccountFailure> { session.authorized { _, token -> api.use(token) } }

        assertEquals(AccountProblem.ServerUnreachable, failure.problem)
        assertIs<AccountStatus.SignedIn>(session.status.value)
    }

    @Test
    fun signingOutForgetsEverythingEvenWithTheServerUnreachable() = runTest {
        session.signIn(HomeServer, "alice", "correct horse")
        api.reachable = false

        session.signOut()

        assertEquals(AccountStatus.SignedOut, session.status.value)
        assertNull(store.load())
    }

    @Test
    fun signingOutTellsAServerThatCanBeReached() = runTest {
        session.signIn(HomeServer, "alice", "correct horse")

        session.signOut()

        assertEquals(1, api.signOuts)
    }

    @Test
    fun deletingTheAccountRemovesItOnTheServerAndSignsOut() = runTest {
        session.signIn(HomeServer, "alice", "correct horse")

        session.deleteAccount()

        assertEquals(1, api.deleted)
        assertEquals(AccountStatus.SignedOut, session.status.value)
        assertNull(store.load())
    }

    @Test
    fun theOnlyAdminStaysSignedInWhenTheServerRefusesTheDeletion() = runTest {
        session.signIn(HomeServer, "alice", "correct horse")
        api.onlyAdmin = true

        val failure = assertFailsWith<AccountFailure> { session.deleteAccount() }

        assertEquals(AccountProblem.LastAdmin, failure.problem)
        assertIs<AccountStatus.SignedIn>(session.status.value)
    }

    @Test
    fun aNewServerIsSetUpAndAnInviteRedeemed() = runTest {
        api.info = api.info.copy(setupRequired = true)
        session.setUp(HomeServer, "alice", "correct horse")
        assertIs<AccountStatus.SignedIn>(session.status.value)

        session.signOut()
        val failure = assertFailsWith<AccountFailure> { session.redeem(HomeServer, "nope", "bob", "battery staple") }
        assertEquals(AccountProblem.InvalidInvite, failure.problem)

        session.redeem(HomeServer, " invite ", "bob", "battery staple")
        assertIs<AccountStatus.SignedIn>(session.status.value)
    }

    @Test
    fun checkingAnAddressAnswersWithTheAddressItWillUse() = runTest {
        val (address, info) = session.check("192.168.1.10:8080")

        assertEquals(HomeServer, address)
        assertEquals("Home shelf", info.name)
    }
}
