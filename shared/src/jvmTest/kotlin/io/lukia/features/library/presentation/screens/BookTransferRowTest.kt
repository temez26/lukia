package io.lukia.features.library.presentation.screens

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.lukia.shared.presentation.theme.LukiaTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** What a book on only one side offers - fetching it down, or sending it up - in each state of doing so. */
@OptIn(ExperimentalTestApi::class)
class BookTransferRowTest {

    private fun ComposeUiTest.show(
        state: BookTransfer,
        actions: BookTransferActions = BookTransferActions(),
        direction: TransferDirection = TransferDirection.Download,
    ) {
        setContent {
            LukiaTheme(darkTheme = false) {
                BookTransferRow(direction = direction, state = state, actions = actions)
            }
        }
    }

    @Test
    fun aBookNotFetchedYetOffersToDownload() = runComposeUiTest {
        var started = false
        show(BookTransfer.Idle, BookTransferActions(onStart = { started = true }))

        onNodeWithText("Download").performClick()

        assertEquals(true, started)
    }

    @Test
    fun aDownloadUnderWaySaysHowFarAndCanBeCancelled() = runComposeUiTest {
        var cancelled = false
        show(BookTransfer.Running(0.42f), BookTransferActions(onCancel = { cancelled = true }))

        onNodeWithText("Downloading… 42%").assertExists()
        onNodeWithText("Cancel").performClick()

        assertEquals(true, cancelled)
    }

    @Test
    fun aFailedDownloadSaysSoAndOffersToTryAgain() = runComposeUiTest {
        var retried = false
        show(BookTransfer.Failed, BookTransferActions(onStart = { retried = true }))

        onNodeWithText("The download did not finish.").assertExists()
        onNodeWithText("Try again").performClick()

        assertEquals(true, retried)
    }

    @Test
    fun aBookOnlyOnThisDeviceOffersToGoToTheServer() = runComposeUiTest {
        var started = false
        show(BookTransfer.Idle, BookTransferActions(onStart = { started = true }), TransferDirection.Upload)

        onNodeWithText("Add to server").performClick()

        assertEquals(true, started)
    }

    @Test
    fun aFailedUploadSaysSo() = runComposeUiTest {
        show(BookTransfer.Failed, direction = TransferDirection.Upload)

        onNodeWithText("The book did not reach the server.").assertExists()
    }
}
