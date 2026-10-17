package io.lukia.features.account

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.lukia.features.account.domain.AccountProblem
import io.lukia.features.account.presentation.AdministrationContent
import io.lukia.features.account.presentation.AdministrationUiState
import io.lukia.shared.presentation.theme.LukiaTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** The admin's section of Account, as drawn. */
@OptIn(ExperimentalTestApi::class)
class AdministrationSettingsTest {

    private fun ComposeUiTest.show(state: AdministrationUiState, onChange: (Boolean) -> Unit = {}) {
        setContent {
            LukiaTheme(darkTheme = false) {
                Box(modifier = Modifier.width(360.dp).padding(16.dp)) {
                    AdministrationContent(
                        state = state,
                        onSignUpOpenChange = onChange,
                        groupColor = MaterialTheme.colorScheme.surface,
                    )
                }
            }
        }
    }

    @Test
    fun theSwitchSaysWhatOpeningTheServerMeans() = runComposeUiTest {
        var changedTo: Boolean? = null
        show(AdministrationUiState(signUpOpen = false)) { changedTo = it }

        onNodeWithText("Administration").assertExists()
        onNodeWithText("While this is on, anyone who can reach this server can create an account.").assertExists()
        onNode(isToggleable()).assertIsOff().performClick()

        assertEquals(true, changedTo)
    }

    /** Until the server has said, there is no switch to show in a position it might not be in. */
    @Test
    fun noSwitchUntilTheServerHasSaid() = runComposeUiTest {
        show(AdministrationUiState(signUpOpen = null))

        onNodeWithText("Allow new accounts").assertDoesNotExist()
    }

    @Test
    fun aRefusalIsSaidUnderTheSection() = runComposeUiTest {
        show(AdministrationUiState(signUpOpen = false, problem = AccountProblem.NotAdmin))

        onNodeWithText("Only this server's admin can do that.").assertExists()
    }
}
