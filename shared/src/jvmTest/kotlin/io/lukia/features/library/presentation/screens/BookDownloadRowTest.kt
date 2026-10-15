package io.lukia.features.library.presentation.screens

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.lukia.shared.presentation.theme.LukiaTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** What a server book's page offers in place of reading it, in each state of fetching it. */
@OptIn(ExperimentalTestApi::class)
class BookDownloadRowTest {

    private fun ComposeUiTest.show(state: BookDownload, actions: BookDownloadActions = BookDownloadActions()) {
        setContent {
            LukiaTheme(darkTheme = false) {
                BookDownloadRow(state = state, actions = actions)
            }
        }
    }

    @Test
    fun aBookNotFetchedYetOffersToDownload() = runComposeUiTest {
        var started = false
        show(BookDownload.Idle, BookDownloadActions(onStart = { started = true }))

        onNodeWithText("Download").performClick()

        assertEquals(true, started)
    }

    @Test
    fun aDownloadUnderWaySaysHowFarAndCanBeCancelled() = runComposeUiTest {
        var cancelled = false
        show(BookDownload.Running(0.42f), BookDownloadActions(onCancel = { cancelled = true }))

        onNodeWithText("Downloading… 42%").assertExists()
        onNodeWithText("Cancel").performClick()

        assertEquals(true, cancelled)
    }

    @Test
    fun aFailedDownloadSaysSoAndOffersToTryAgain() = runComposeUiTest {
        var retried = false
        show(BookDownload.Failed, BookDownloadActions(onStart = { retried = true }))

        onNodeWithText("The download did not finish.").assertExists()
        onNodeWithText("Try again").performClick()

        assertEquals(true, retried)
    }
}
