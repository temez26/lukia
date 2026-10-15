package io.lukia.features.account

import io.lukia.MainDispatcherTest
import io.lukia.features.account.application.AccountSession
import io.lukia.features.account.domain.AccountStatus
import io.lukia.features.account.presentation.AccountForm
import io.lukia.features.account.presentation.AccountViewModel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** Signing in from one form: the address, who, and the password, sent at once. */
class AccountViewModelTest : MainDispatcherTest() {

    private val api = FakeServerApi()
    private val store = InMemoryAccountStore()

    private fun viewModel() = AccountViewModel(AccountSession(api, store))

    @Test
    fun oneSendSignsIn() = runTest {
        val viewModel = viewModel()

        viewModel.submit(HomeServer, "alice", "correct horse")

        assertIs<AccountStatus.SignedIn>(viewModel.state.value.status)
        assertNull(viewModel.state.value.problem)
    }

    @Test
    fun aServerWithNoAccountsAsksBeforeMakingTheAdmin() = runTest {
        api.info = api.info.copy(setupRequired = true)
        val viewModel = viewModel()

        viewModel.submit(HomeServer, "alice", "correct horse")

        assertEquals(AccountForm.SetUp("Home shelf"), viewModel.state.value.form)
        assertEquals(AccountStatus.SignedOut, viewModel.state.value.status)

        viewModel.submit(HomeServer, "alice", "correct horse")

        assertIs<AccountStatus.SignedIn>(viewModel.state.value.status)
    }

    @Test
    fun theLastSignInIsThereAfterSigningOut() = runTest {
        val first = viewModel()
        first.submit(HomeServer, "alice", "correct horse")
        first.signOut()

        assertEquals(HomeServer, viewModel().lastSignIn?.address)
        assertEquals("alice", viewModel().lastSignIn?.username)
    }
}
