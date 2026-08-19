package io.lukia.features.library.presentation.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.core.domain.DocumentInfo
import io.lukia.core.domain.DocumentType
import io.lukia.core.domain.PickedDocument
import io.lukia.core.presentation.UiState
import io.lukia.features.library.presentation.LibraryQuery
import io.lukia.features.library.presentation.asLibrary
import io.lukia.features.library.presentation.facets
import io.lukia.features.library.presentation.matching
import io.lukia.shared.presentation.theme.LukiaTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class LibraryContentTest {

    private fun book(
        title: String,
        type: DocumentType = DocumentType.PDF,
        favorite: Boolean = false
    ): DocumentInfo {
        val extension = if (type == DocumentType.EPUB) "epub" else "pdf"

        return DocumentInfo(
            document = PickedDocument(
                fileName = "$title.$extension",
                extension = extension,
                location = "/library/$title.$extension"
            ),
            type = type,
            exists = true,
            sizeBytes = 1024L,
            isFavorite = favorite
        )
    }

    /**
     * Small on purpose: the grid is lazy, so a library that overflows the test
     * window would leave the import slot uncomposed and the assertions below
     * would pass or fail for the wrong reason.
     */
    private val library = listOf(
        book("Moby Dick", favorite = true),
        book("The Hobbit", DocumentType.EPUB)
    )

    private fun ComposeUiTest.setLibrary(
        documents: List<DocumentInfo> = library,
        width: Dp = 1000.dp,
        onDocumentDetails: (DocumentInfo) -> Unit = {}
    ) {
        setContent {
            // The query is the ViewModel's in the app, so the test plays that
            // part: it holds what the controls ask for and narrows the library
            // through the same functions the ViewModel narrows it with. Anything
            // less would be asserting that a chip changes a variable rather than
            // that it changes what is on screen.
            var query by remember { mutableStateOf(LibraryQuery()) }
            val books = remember(documents) { documents.asLibrary(emptyMap()) }
            val facets = remember(books) { books.facets() }

            // Sized explicitly: the layout branches on the window's width class,
            // and a test that let the harness pick the window would be asserting
            // on whichever branch that happened to land in.
            Box(modifier = Modifier.size(width, 900.dp)) {
                LukiaTheme(darkTheme = false) {
                    LibraryContent(
                        uiState = UiState.Success(documents),
                        books = books.matching(query),
                        facets = facets,
                        query = query,
                        onQueryChange = { query = it },
                        onImport = {},
                        onImportFolder = {},
                        onDocumentClick = {},
                        onDocumentDetails = onDocumentDetails,
                        onToggleFavorite = {},
                        onRefresh = {},
                        onDelete = {},
                        onRetry = {},
                        isImporting = false
                    )
                }
            }
        }
    }

    /** Opens one book's actions the way a long press on its card does. */
    private fun ComposeUiTest.openBookMenu(title: String) {
        onNodeWithText(title).performTouchInput { longClick() }
    }

    /** Presses a chip or a menu row by the words on it. */
    private fun ComposeUiTest.press(label: String) {
        onNode(hasText(label) and hasClickAction()).performClick()
    }

    /** Opens the format chip's menu and chooses [format] from it. */
    private fun ComposeUiTest.selectFormat(format: String) {
        press("Format")
        press(format)
    }

    @Test
    fun theImportSlotEndsTheLibrary() = runComposeUiTest {
        setLibrary()

        onNodeWithText("Add Books").assertIsDisplayed()
    }

    /**
     * A slot offering to add a book to a filtered view promises what it cannot
     * keep: the book lands in the library but not on the screen that took it.
     */
    @Test
    fun theImportSlotIsNotOfferedUnderAFilter() = runComposeUiTest {
        setLibrary()

        press("Favourites")

        onNodeWithText("Add Books").assertDoesNotExist()
    }

    /** The formats a library holds are chosen from the chip's own menu. */
    @Test
    fun aFormatChosenFromTheMenuNarrowsTheGrid() = runComposeUiTest {
        setLibrary()

        selectFormat("EPUB")

        onNodeWithText("The Hobbit").assertIsDisplayed()
        onNodeWithText("Moby Dick").assertDoesNotExist()
    }

    @Test
    fun theImportSlotComesBackOnceTheFormatIsCleared() = runComposeUiTest {
        setLibrary()

        selectFormat("EPUB")
        onNodeWithText("Add Books").assertDoesNotExist()

        // The chip now wears the chosen format's name, so that is what opens it.
        press("EPUB")
        press("All formats")

        onNodeWithText("Add Books").assertIsDisplayed()
    }

    /** The count in the search field follows whatever the filter left behind. */
    @Test
    fun theSearchFieldCountsWhatIsSearchable() = runComposeUiTest {
        setLibrary()

        onNodeWithText("Search 2 books").assertIsDisplayed()

        press("Favourites")

        onNodeWithText("Search 1 book").assertIsDisplayed()
    }

    /**
     * A window with room for both keeps the library on screen and lays the
     * details over it, rather than navigating away from what was being browsed.
     */
    @Test
    fun aWideWindowOpensDetailsOverTheLibrary() = runComposeUiTest {
        var navigatedTo: DocumentInfo? = null
        setLibrary(width = 1000.dp, onDocumentDetails = { navigatedTo = it })

        openBookMenu("Moby Dick")
        onNodeWithText("Book Details").performClick()

        // The panel is up...
        onNodeWithText("Information").assertIsDisplayed()
        // ...the library is still behind it...
        onNodeWithText("The Hobbit").assertIsDisplayed()
        // ...and nothing was navigated to.
        assertNull(navigatedTo)
    }

    /** A phone has room for one thing at a time, so it still goes to a screen. */
    @Test
    fun aNarrowWindowNavigatesToTheDetailScreen() = runComposeUiTest {
        var navigatedTo: DocumentInfo? = null
        setLibrary(width = 390.dp, onDocumentDetails = { navigatedTo = it })

        openBookMenu("Moby Dick")
        onNodeWithText("Book Details").performClick()

        assertEquals("Moby Dick.pdf", navigatedTo?.document?.fileName)
        onNodeWithText("Information").assertDoesNotExist()
    }

    /** Closing puts the reader back where they were, with nothing else changed. */
    @Test
    fun theDetailPanelCloses() = runComposeUiTest {
        setLibrary(width = 1000.dp)

        openBookMenu("Moby Dick")
        onNodeWithText("Book Details").performClick()
        onNodeWithText("Information").assertIsDisplayed()

        onNodeWithContentDescription("Close").performClick()

        onNodeWithText("Information").assertDoesNotExist()
        onNodeWithText("Add Books").assertIsDisplayed()
    }
}
