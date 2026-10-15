package io.lukia.features.account

import io.lukia.features.account.application.AccountSession
import io.lukia.features.account.domain.AccountFailure
import io.lukia.features.account.domain.AccountProblem
import io.lukia.features.account.domain.AccountStatus
import io.lukia.features.account.infrastructure.KtorServerApi
import io.lukia.features.account.infrastructure.serverHttpClient
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/**
 * The app's account code against the real server, over a real socket, with the
 * client the app ships. Everything else in this package tests one side against
 * a stand-in for the other; this is where the two halves of the wire have to
 * agree.
 */
class ServerContractTest {

    private val server = TestSyncServer()
    private val address = server.address
    private val session = AccountSession(KtorServerApi(serverHttpClient()), InMemoryAccountStore())

    @AfterTest
    fun stop() = server.close()

    @Test
    fun aNewServerIsSetUpFromTheAppAndSignedOutOf() = runBlocking {
        val (checked, info) = session.check(address)
        assertEquals(true, info.setupRequired)
        assertEquals("Contract shelf", info.name)

        session.setUp(checked, "admin", "correct horse battery")
        val signedIn = assertIs<AccountStatus.SignedIn>(session.status.value)
        assertEquals("admin", signedIn.account.username)
        assertEquals(true, signedIn.account.isAdmin)

        val answer = session.authorized { at, token -> KtorServerApi(serverHttpClient()).me(at, token) }
        assertEquals("admin" to true, answer)

        session.signOut()
        assertEquals(AccountStatus.SignedOut, session.status.value)
    }

    @Test
    fun theServersRefusalsArriveAsTheProblemsTheAppNames() = runBlocking {
        session.setUp(address, "admin", "correct horse battery")
        session.signOut()

        assertEquals(AccountProblem.SignInRejected, problemOf { session.signIn(address, "admin", "wrong password") })
        assertEquals(
            AccountProblem.AlreadySetUp,
            problemOf {
                session.setUp(address, "other", "correct horse battery")
            },
        )
        assertEquals(
            AccountProblem.InvalidInvite,
            problemOf {
                session.redeem(address, "nope", "bob", "battery staple")
            },
        )
        assertEquals(AccountProblem.InvalidPassword, problemOf { session.redeem(address, "nope", "bob", "short") })
    }

    @Test
    fun anAddressWithNothingListeningIsUnreachable() = runBlocking {
        server.stop()

        assertEquals(AccountProblem.ServerUnreachable, problemOf { session.check(address) })
    }

    private suspend fun problemOf(block: suspend () -> Unit): AccountProblem =
        assertFailsWith<AccountFailure> { block() }.problem
}
