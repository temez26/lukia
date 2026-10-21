package io.lukia.features.library.presentation

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.lukia.core.domain.document.BookMetadata
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.domain.document.DocumentType
import io.lukia.core.domain.document.PickedDocument
import io.lukia.shared.presentation.theme.LukiaTheme
import kotlin.test.Test

/** A book's details docked beside the shelf on a window, rather than laid over it. */
@OptIn(ExperimentalTestApi::class)
class BookDetailSidePanelTest {

    private val book = DocumentInfo(
        document = PickedDocument("piranesi.epub", "epub", "/library/piranesi.epub"),
        type = DocumentType.EPUB,
        exists = true,
        sizeBytes = 1_200_000L,
        metadata = BookMetadata(title = "Piranesi"),
    )

    private var shown by mutableStateOf<DocumentInfo?>(book)

    private fun ComposeUiTest.showPanel() {
        setContent {
            LukiaTheme(darkTheme = false) {
                Row {
                    Text(ShelfText)
                    BookDetailSidePanel(
                        document = shown,
                        progress = null,
                        onDismiss = { shown = null },
                        onRead = {},
                        onToggleFavorite = {},
                        onRefresh = {},
                        onDelete = {},
                    )
                }
            }
        }
    }

    @Test
    fun theShelfStaysInReachBesideIt() = runComposeUiTest {
        showPanel()

        onNodeWithText("Information").assertIsDisplayed()
        onNodeWithText(ShelfText).assertIsDisplayed()
    }

    @Test
    fun closingItTakesItAway() = runComposeUiTest {
        showPanel()

        onNodeWithContentDescription("Close").performClick()
        waitForIdle()

        onNodeWithText("Information").assertDoesNotExist()
        onNodeWithText(ShelfText).assertIsDisplayed()
    }

    private companion object {
        const val ShelfText = "the shelf"
    }
}
