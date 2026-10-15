package io.lukia.features.sync

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.lukia.features.account.domain.AccountProblem
import io.lukia.features.sync.application.SyncStatus
import io.lukia.features.sync.presentation.SyncStatusContent
import io.lukia.features.sync.presentation.SyncStatusUi
import io.lukia.shared.presentation.theme.LukiaTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** Whether the device is level with the server, as the Account page says it. */
@OptIn(ExperimentalTestApi::class)
class SyncStatusCardTest {

    private val now = 10_000_000L

    private fun ComposeUiTest.show(state: SyncStatusUi, onSyncNow: () -> Unit = {}) {
        setContent {
            LukiaTheme(darkTheme = false) {
                SyncStatusContent(state, now, onSyncNow, MaterialTheme.colorScheme.surface)
            }
        }
    }

    @Test
    fun upToDateSaysWhenItLastChecked() = runComposeUiTest {
        var asked = 0
        show(SyncStatusUi(SyncStatus(lastSyncedAt = now - 3 * MINUTE), serverName = "Home shelf")) { asked++ }

        onNodeWithText("Up to date").assertExists()
        onNodeWithText("Checked 3 minutes ago").assertExists()
        onNodeWithText("Sync now").performClick()

        assertEquals(1, asked)
    }

    @Test
    fun changesNotSentYetAreCounted() = runComposeUiTest {
        show(SyncStatusUi(SyncStatus(lastSyncedAt = now), pending = 2, serverName = "Home shelf"))

        onNodeWithText("2 changes waiting to be sent").assertExists()
        onNodeWithText("Checked just now").assertExists()
    }

    @Test
    fun aServerOutOfReachSaysWhenItWillBeTriedAgain() = runComposeUiTest {
        show(
            SyncStatusUi(
                SyncStatus(problem = AccountProblem.ServerUnreachable, retryAt = now + 2 * MINUTE),
                pending = 1,
                serverName = "Home shelf",
            ),
        )

        onNodeWithText("Can’t reach Home shelf").assertExists()
        onNodeWithText("Trying again in 2 minutes.").assertExists()
        onNodeWithText("1 change waiting to be sent").assertExists()
    }

    @Test
    fun aRoundUnderWayCannotBeStartedAgain() = runComposeUiTest {
        show(SyncStatusUi(SyncStatus(running = true), serverName = "Home shelf"))

        onNodeWithText("Syncing…").assertExists()
        onNodeWithText("Sync now").assertIsNotEnabled()
    }

    @Test
    fun signedOutThereIsNothingToShow() = runComposeUiTest {
        show(SyncStatusUi())

        onNodeWithText("Sync now").assertDoesNotExist()
    }

    private companion object {
        const val MINUTE = 60_000L
    }
}
