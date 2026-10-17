package io.lukia.features.account

import io.lukia.MainDispatcherTest
import io.lukia.features.account.application.AccountSession
import io.lukia.features.account.application.ServerAdministration
import io.lukia.features.account.domain.AccountProblem
import io.lukia.features.account.domain.Person
import io.lukia.features.account.presentation.AdministrationViewModel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The admin's section: the switch for new accounts - read, changed at once, put back if refused - and the people. */
class AdministrationViewModelTest : MainDispatcherTest() {

    private val api = FakeServerApi()
    private val store = InMemoryAccountStore()
    private val session = AccountSession(api, store)

    private suspend fun signedInViewModel(): AdministrationViewModel {
        session.signIn(HomeServer, "alice", "correct horse")
        return AdministrationViewModel(ServerAdministration(session, api))
    }

    @Test
    fun theSwitchShowsHowTheServerStands() = runTest {
        api.signUpSetting = true
        val viewModel = signedInViewModel()

        assertNull(viewModel.state.value.signUpOpen)
        viewModel.load()

        assertEquals(true, viewModel.state.value.signUpOpen)
    }

    @Test
    fun turningItOnTellsTheServer() = runTest {
        val viewModel = signedInViewModel()
        viewModel.load()

        viewModel.setSignUpOpen(true)

        assertEquals(true, viewModel.state.value.signUpOpen)
        assertEquals(true, api.signUpSetting)
    }

    /** Somebody who stopped being the admin is told why, and the switch does not pretend it worked. */
    @Test
    fun aRefusalPutsTheSwitchBackAndSaysWhy() = runTest {
        val viewModel = signedInViewModel()
        viewModel.load()
        api.notAdmin = true

        viewModel.setSignUpOpen(true)

        assertEquals(false, viewModel.state.value.signUpOpen)
        assertEquals(AccountProblem.NotAdmin, viewModel.state.value.problem)
    }

    @Test
    fun everyoneIsListedOnceTheServerHasSaid() = runTest {
        val bob = Person("bob", isAdmin = false, 2, 3, 1, 0, readingMs = 60_000, lastReadAt = null)
        api.people = listOf(bob)
        val viewModel = signedInViewModel()

        assertNull(viewModel.state.value.people)
        viewModel.load()

        assertEquals(listOf(bob), viewModel.state.value.people)
    }

    @Test
    fun aResetIsSentAndSaidToHaveHappened() = runTest {
        val viewModel = signedInViewModel()

        viewModel.resetPassword("bob", "a brand new password")

        assertEquals(listOf("bob" to "a brand new password"), api.passwordResets)
        assertEquals("bob", viewModel.state.value.passwordResetFor)
        assertEquals(false, viewModel.state.value.busy)
    }

    @Test
    fun aRemovedAccountLeavesTheList() = runTest {
        api.people = listOf(person("alice"), person("bob"))
        val viewModel = signedInViewModel()
        viewModel.load()

        viewModel.removeAccount("bob")

        assertEquals(listOf("alice"), viewModel.state.value.people?.map { it.username })
    }

    /** Said under People, where the button was pressed, rather than under the switch. */
    @Test
    fun aRefusedRemovalIsSaidUnderPeople() = runTest {
        val viewModel = signedInViewModel()
        api.notAdmin = true

        viewModel.removeAccount("bob")

        assertEquals(AccountProblem.NotAdmin, viewModel.state.value.peopleProblem)
        assertNull(viewModel.state.value.problem)
    }

    private fun person(username: String) = Person(
        username,
        isAdmin = false,
        0,
        0,
        0,
        0,
        readingMs = 0,
        lastReadAt = null,
    )
}
