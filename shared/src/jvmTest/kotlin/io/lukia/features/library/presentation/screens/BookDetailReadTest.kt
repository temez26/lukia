package io.lukia.features.library.presentation.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.domain.document.DocumentType
import io.lukia.core.domain.document.PickedDocument
import io.lukia.core.domain.reading.ReadingProgress
import io.lukia.core.presentation.UiState
import io.lukia.features.library.application.BookDetails
import io.lukia.shared.presentation.theme.LukiaTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The detail page's way into the book: a button that says what it does, as the
 * reading apps set theirs, and Mark as Unread for a book that has been started.
 */
@OptIn(ExperimentalTestApi::class)
class BookDetailReadTest {

    private val document = DocumentInfo(
        document = PickedDocument(fileName = "book.epub", extension = "epub", location = "/library/book.epub"),
        type = DocumentType.EPUB,
        exists = true,
        sizeBytes = 1024L,
    )

    private var read = 0
    private var markedUnread = 0

    private fun ComposeUiTest.setDetails(progress: ReadingProgress?) {
        setContent {
            Box(modifier = Modifier.size(400.dp, 900.dp)) {
                LukiaTheme(darkTheme = false) {
                    BookDetailContent(
                        uiState = UiState.Success(BookDetails(document, progress)),
                        onBack = {},
                        onRead = { read++ },
                        onRetry = {},
                        onToggleFavorite = {},
                        onRefresh = {},
                        onDelete = {},
                        onMarkUnread = { markedUnread++ },
                    )
                }
            }
        }
    }

    @Test
    fun aStartedBookOffersToContinueReading() = runComposeUiTest {
        setDetails(ReadingProgress(pageIndex = 4, pageCount = 20))

        onNode(hasText("Continue reading") and hasClickAction()).performClick()

        assertEquals(1, read)
    }

    @Test
    fun anUnopenedBookOffersToStartReading() = runComposeUiTest {
        setDetails(progress = null)

        onNode(hasText("Start reading") and hasClickAction()).assertExists()
    }

    @Test
    fun aStartedBookCanBeMarkedUnreadFromItsMenu() = runComposeUiTest {
        setDetails(ReadingProgress(pageIndex = 4, pageCount = 20))

        onNodeWithContentDescription("Book actions").performClick()
        onNode(hasText("Mark as Unread") and hasClickAction()).performClick()

        assertEquals(1, markedUnread)
    }

    /** Nothing to put back for a book never opened, so the row is not offered. */
    @Test
    fun anUnopenedBookIsNotOfferedMarkAsUnread() = runComposeUiTest {
        setDetails(progress = null)

        onNodeWithContentDescription("Book actions").performClick()

        assertTrue(onAllNodes(hasText("Mark as Unread")).fetchSemanticsNodes().isEmpty())
    }
}
