package io.lukia.features.library.presentation.screens

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.lukia.core.domain.DocumentInfo
import io.lukia.core.domain.DocumentType
import io.lukia.core.domain.PickedDocument
import io.lukia.core.presentation.UiState
import io.lukia.shared.presentation.theme.LukiaTheme
import kotlin.test.Test

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

    private fun ComposeUiTest.setLibrary(documents: List<DocumentInfo> = library) {
        setContent {
            LukiaTheme(darkTheme = false) {
                LibraryContent(
                    uiState = UiState.Success(documents),
                    onImport = {},
                    onImportFolder = {},
                    onDocumentClick = {},
                    onDocumentDetails = {},
                    onToggleFavorite = {},
                    onDelete = {},
                    onRetry = {},
                    isImporting = false
                )
            }
        }
    }

    private fun ComposeUiTest.selectFilter(label: String) {
        onNode(hasText(label) and hasClickAction()).performClick()
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

        selectFilter("Favourites")

        onNodeWithText("Add Books").assertDoesNotExist()
    }

    @Test
    fun theImportSlotComesBackOnReturningToAll() = runComposeUiTest {
        setLibrary()

        selectFilter("EPUB")
        onNodeWithText("Add Books").assertDoesNotExist()

        selectFilter("All")
        onNodeWithText("Add Books").assertIsDisplayed()
    }

    /** The count in the search field follows whatever the filter left behind. */
    @Test
    fun theSearchFieldCountsWhatIsSearchable() = runComposeUiTest {
        setLibrary()

        onNodeWithText("Search 2 books").assertIsDisplayed()

        selectFilter("Favourites")

        onNodeWithText("Search 1 book").assertIsDisplayed()
    }
}
