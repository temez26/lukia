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
import io.lukia.features.account.domain.Person
import io.lukia.features.account.presentation.AdministrationContent
import io.lukia.features.account.presentation.AdministrationUiState
import io.lukia.shared.presentation.theme.LukiaTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

/** The admin's section of Account, as drawn. */
@OptIn(ExperimentalTestApi::class)
class AdministrationSettingsTest {

    private val today = LocalDate(2026, 10, 17)

    private fun ComposeUiTest.show(state: AdministrationUiState, onChange: (Boolean) -> Unit = {}) {
        setContent {
            LukiaTheme(darkTheme = false) {
                Box(modifier = Modifier.width(360.dp).padding(16.dp)) {
                    AdministrationContent(
                        state = state,
                        onSignUpOpenChange = onChange,
                        today = today,
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

    @Test
    fun eachPersonShowsWhatTheyHaveRead() = runComposeUiTest {
        val threeDaysAgo = LocalDateTime(2026, 10, 14, 12, 0).toInstant(TimeZone.currentSystemDefault())
        show(
            AdministrationUiState(
                signUpOpen = true,
                people = listOf(
                    Person("admin", isAdmin = true, 0, 0, 0, 0, readingMs = 0, lastReadAt = null),
                    Person("bob", isAdmin = false, 1, 3, 2, 1, readingMs = 5_400_000, lastReadAt = threeDaysAgo),
                ),
            ),
        )

        onNodeWithText("People").assertExists()
        onNodeWithText("Admin").assertExists()
        onNodeWithText("No reading time yet").assertExists()
        onNodeWithText("1 book added · 3 started · 2 finished · 1 favourite").assertExists()
        onNodeWithText("Reading time: 1h 30m · Last read 3 days ago").assertExists()
    }
}
