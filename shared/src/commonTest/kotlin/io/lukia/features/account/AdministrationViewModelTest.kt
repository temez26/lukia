package io.lukia.features.account

import io.lukia.MainDispatcherTest
import io.lukia.features.account.application.AccountSession
import io.lukia.features.account.application.ServerAdministration
import io.lukia.features.account.domain.AccountProblem
import io.lukia.features.account.presentation.AdministrationViewModel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The admin's switch for new accounts: read from the server, changed at once, put back if refused. */
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
}
