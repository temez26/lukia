package io.lukia.features.account

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.lukia.features.account.domain.AccountProblem
import io.lukia.features.account.domain.AccountStatus
import io.lukia.features.account.domain.ServerAccount
import io.lukia.features.account.domain.ServerInfo
import io.lukia.features.account.presentation.AccountActions
import io.lukia.features.account.presentation.AccountSettingsContent
import io.lukia.features.account.presentation.AccountStep
import io.lukia.features.account.presentation.AccountUiState
import io.lukia.shared.presentation.theme.LukiaTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** The Account page in each of its states, as somebody signing in meets them. */
@OptIn(ExperimentalTestApi::class)
class AccountSettingsTest {

    private val home = ServerInfo("Home shelf", "1.0.1", setupRequired = false)
    private val alice = ServerAccount(HomeServer, "Home shelf", "alice", isAdmin = false)

    private fun ComposeUiTest.show(state: AccountUiState, actions: AccountActions = AccountActions()) {
        setContent {
            LukiaTheme(darkTheme = false) {
                Box(modifier = Modifier.width(360.dp).padding(16.dp)) {
                    AccountSettingsContent(
                        state = state,
                        actions = actions,
                        groupColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
                    )
                }
            }
        }
    }

    @Test
    fun signedOutAsksForAnAddressAndConnectsOnceThereIsOne() = runComposeUiTest {
        show(AccountUiState())

        onNodeWithText("Connect").assertIsNotEnabled()
        onNodeWithText("Address").assertExists()
    }

    @Test
    fun aNewServerOffersToCreateItsAdmin() = runComposeUiTest {
        show(
            AccountUiState(
                step = AccountStep.Credentials(HomeServer, home.copy(setupRequired = true), withInvite = false),
            ),
        )

        onNodeWithText("Home shelf has no accounts yet. The one you create now is its admin.").assertExists()
        onNodeWithText("Create account").assertExists()
        // There is nobody to have invited anyone yet.
        onNodeWithText("I have an invite code").assertDoesNotExist()
    }

    @Test
    fun anInviteCanBeUsedInsteadOfAPassword() = runComposeUiTest {
        var switchedTo: Boolean? = null
        show(
            AccountUiState(step = AccountStep.Credentials(HomeServer, home, withInvite = false)),
            AccountActions(onUseInvite = { switchedTo = it }),
        )

        onNodeWithText("Sign in").assertExists()
        onNodeWithText("I have an invite code").performClick()

        assertEquals(true, switchedTo)
    }

    @Test
    fun signedInSaysWhereAndAsWhomAndOffersToSignOut() = runComposeUiTest {
        var signedOut = false
        show(AccountUiState(status = AccountStatus.SignedIn(alice)), AccountActions(onSignOut = { signedOut = true }))

        onNodeWithText("Signed in to Home shelf as alice.").assertExists()
        onNodeWithText("Sign out").assertIsEnabled().performClick()

        assertEquals(true, signedOut)
    }

    @Test
    fun aSignInTheServerStoppedAcceptingAsksForThePasswordOnly() = runComposeUiTest {
        show(AccountUiState(status = AccountStatus.NeedsSignIn(alice)))

        onNodeWithText("Home shelf asks for your password again.").assertExists()
        onNodeWithText("Address").assertDoesNotExist()
        onNodeWithText("Username").assertDoesNotExist()
    }

    @Test
    fun whatWentWrongIsSaidUnderTheForm() = runComposeUiTest {
        show(AccountUiState(problem = AccountProblem.InsecureAddress))

        onNodeWithText("Use https:// for a server outside your own network.").assertExists()
    }

    @Test
    fun whileARequestIsOutTheButtonSaysSoAndWaits() = runComposeUiTest {
        show(AccountUiState(step = AccountStep.Credentials(HomeServer, home, withInvite = false), busy = true))

        onNodeWithText("Signing in…").assertIsNotEnabled()
        onNodeWithText("Use another server").assertIsNotEnabled()
    }
}
