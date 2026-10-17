package io.lukia.features.account

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.lukia.features.account.domain.AccountProblem
import io.lukia.features.account.domain.Person
import io.lukia.features.account.presentation.AdministrationContent
import io.lukia.features.account.presentation.AdministrationUiState
import io.lukia.features.account.presentation.PeopleActions
import io.lukia.shared.presentation.theme.LukiaTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The admin's section of Account, as drawn. */
@OptIn(ExperimentalTestApi::class)
class AdministrationSettingsTest {

    private val today = LocalDate(2026, 10, 17)

    private fun ComposeUiTest.show(
        state: AdministrationUiState,
        actions: PeopleActions = PeopleActions(),
        onChange: (Boolean) -> Unit = {},
    ) {
        setContent {
            LukiaTheme(darkTheme = false) {
                Box(modifier = Modifier.width(360.dp).padding(16.dp)) {
                    AdministrationContent(
                        state = state,
                        onSignUpOpenChange = onChange,
                        peopleActions = actions,
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
        show(AdministrationUiState(signUpOpen = false), onChange = { changedTo = it })

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

    @Test
    fun yourOwnRowOffersNeitherResetNorRemoval() = runComposeUiTest {
        show(AdministrationUiState(you = "admin", people = listOf(person("admin", isAdmin = true), person("bob"))))

        onAllNodesWithText("Reset password").assertCountEquals(1)
        onAllNodesWithText("Remove account").assertCountEquals(1)
    }

    @Test
    fun removingAnAccountIsAskedFirst() = runComposeUiTest {
        var removed: String? = null
        show(
            AdministrationUiState(you = "admin", people = listOf(person("admin", isAdmin = true), person("bob"))),
            actions = PeopleActions(onRemove = { removed = it }),
        )

        onNodeWithText("Remove account").performClick()
        onNodeWithText("Remove bob?").assertExists()
        assertNull(removed)
        onNode(hasText("Remove") and hasAnyAncestor(isDialog())).performClick()

        assertEquals("bob", removed)
    }

    /** The reset cannot be sent with a password the server would refuse. */
    @Test
    fun aResetWaitsForAPasswordLongEnough() = runComposeUiTest {
        var reset: Pair<String, String>? = null
        show(
            AdministrationUiState(you = "admin", people = listOf(person("bob"))),
            actions = PeopleActions(onResetPassword = { name, password -> reset = name to password }),
        )
        val confirm = hasText("Reset password") and hasAnyAncestor(isDialog())

        onNodeWithText("Reset password").performClick()
        onNode(hasSetTextAction()).performTextInput("too short")
        onNode(confirm).assertIsNotEnabled()
        onNode(hasSetTextAction()).performTextInput(" no longer")
        onNode(confirm).performClick()

        assertEquals("bob" to "too short no longer", reset)
    }

    @Test
    fun aResetIsSaidSoTheAdminPassesItOn() = runComposeUiTest {
        show(AdministrationUiState(you = "admin", people = listOf(person("bob")), passwordResetFor = "bob"))

        onNodeWithText("Password changed for bob. Pass the new password on.").assertExists()
    }

    private fun person(username: String, isAdmin: Boolean = false) =
        Person(username, isAdmin, 0, 0, 0, 0, readingMs = 0, lastReadAt = null)
}
