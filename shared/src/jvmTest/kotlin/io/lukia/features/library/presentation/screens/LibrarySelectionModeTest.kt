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
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.lukia.core.domain.DocumentInfo
import io.lukia.core.domain.DocumentType
import io.lukia.core.domain.PickedDocument
import io.lukia.core.presentation.UiState
import io.lukia.features.library.presentation.BulkAction
import io.lukia.features.library.presentation.LibraryQuery
import io.lukia.features.library.presentation.LibrarySelection
import io.lukia.features.library.presentation.asLibrary
import io.lukia.features.library.presentation.facets
import io.lukia.features.library.presentation.matching
import io.lukia.shared.presentation.theme.LukiaTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Picking books off the shelf: how the mode is entered, what it puts on screen,
 * and what leaves it again.
 */
@OptIn(ExperimentalTestApi::class)
class LibrarySelectionModeTest {

    private val library = listOf(
        book("Moby Dick", favorite = true),
        book("The Hobbit", DocumentType.EPUB),
    )

    private fun book(
        title: String,
        type: DocumentType = DocumentType.PDF,
        favorite: Boolean = false,
    ): DocumentInfo {
        val extension = if (type == DocumentType.EPUB) "epub" else "pdf"

        return DocumentInfo(
            document = PickedDocument(
                fileName = "$title.$extension",
                extension = extension,
                location = "/library/$title.$extension",
            ),
            type = type,
            exists = true,
            sizeBytes = 1024L,
            isFavorite = favorite,
        )
    }

    /**
     * The test plays the view model's part: it holds the selection and changes
     * it through the same calls the model makes, so what is asserted is what the
     * controls do to the screen rather than that they set a variable.
     */
    private fun ComposeUiTest.setLibrary(
        initial: LibrarySelection = LibrarySelection.None,
        running: BulkAction? = null,
        onDelete: () -> Unit = {},
    ) {
        setContent {
            var selection by remember { mutableStateOf(initial) }
            val books = remember { library.asLibrary(emptyMap()) }
            val facets = remember(books) { books.facets() }

            Box(modifier = Modifier.size(1000.dp, 900.dp)) {
                LukiaTheme(darkTheme = false) {
                    LibraryContent(
                        uiState = UiState.Success(library),
                        books = books.matching(LibraryQuery()),
                        facets = facets,
                        query = LibraryQuery(),
                        onQueryChange = {},
                        onImport = {},
                        onImportFolder = {},
                        onDocumentClick = {},
                        onDocumentDetails = {},
                        onToggleFavorite = {},
                        onRefresh = {},
                        onDelete = {},
                        onRetry = {},
                        isImporting = false,
                        selection = LibrarySelectionUi(
                            selection = selection,
                            running = running,
                            onStart = { selection = LibrarySelection(active = true) },
                            onStop = { selection = LibrarySelection.None },
                            onToggle = { location ->
                                selection = selection.copy(
                                    locations = if (location in selection) {
                                        selection.locations - location
                                    } else {
                                        selection.locations + location
                                    },
                                )
                            },
                            onToggleAll = {
                                val all = library.map { it.document.location }.toSet()

                                selection = selection.copy(
                                    locations = if (selection.locations == all) emptySet() else all,
                                )
                            },
                            onDelete = onDelete,
                        ),
                    )
                }
            }
        }
    }

    private fun ComposeUiTest.press(label: String) {
        onNode(hasText(label) and hasClickAction()).performClick()
    }

    @Test
    fun theShelfOffersToSelectBooksFromABooksOwnMenu() = runComposeUiTest {
        setLibrary()

        onNodeWithText("Moby Dick").performTouchInput { longClick() }

        onNodeWithText("Select Books").assertIsDisplayed()
    }

    /** Reaching a shelf-wide mode by holding one book is a way in nobody finds. */
    @Test
    fun theShelfAlsoOffersItFromTheBarsOwnMenu() = runComposeUiTest {
        setLibrary()

        onNodeWithContentDescription("Library actions").performClick()

        onNodeWithText("Select Books").assertIsDisplayed()
    }

    @Test
    fun choosingItPutsTheShelfIntoSelection() = runComposeUiTest {
        setLibrary()

        onNodeWithContentDescription("Library actions").performClick()
        press("Select Books")

        onNodeWithText("Select books").assertIsDisplayed()
        onNodeWithText("Select All").assertIsDisplayed()
    }

    /** Searching and selecting are answers to different questions. */
    @Test
    fun theSearchBarIsNotThereWhileSelecting() = runComposeUiTest {
        setLibrary(initial = LibrarySelection(active = true))

        assertEquals(
            0,
            onAllNodes(hasContentDescription("Library actions")).fetchSemanticsNodes().size,
        )
    }

    @Test
    fun aCardIsTickedByPressingIt() = runComposeUiTest {
        setLibrary(initial = LibrarySelection(active = true))

        onNodeWithText("Moby Dick").performClick()

        onNodeWithText("1 selected").assertIsDisplayed()
    }

    @Test
    fun selectAllTicksTheWholeShelfAndThenClearsIt() = runComposeUiTest {
        setLibrary(initial = LibrarySelection(active = true))

        press("Select All")
        onNodeWithText("2 selected").assertIsDisplayed()

        press("Deselect All")
        onNodeWithText("Select books").assertIsDisplayed()
    }

    @Test
    fun doneLeavesTheModeAndBringsTheSearchBarBack() = runComposeUiTest {
        setLibrary(initial = LibrarySelection(active = true))

        press("Done")

        onNodeWithContentDescription("Library actions").assertIsDisplayed()
    }

    /** Nothing is deleted without the question being asked first. */
    @Test
    fun deletingAsksBeforeItActs() = runComposeUiTest {
        var deleted = false
        setLibrary(
            initial = LibrarySelection(
                active = true,
                locations = setOf(library.first().document.location),
            ),
            onDelete = { deleted = true },
        )

        onNodeWithContentDescription("Delete from library").performClick()

        onNodeWithText("Delete 1 book from your library?").assertIsDisplayed()
        assertTrue(!deleted, "the books went before the question was answered")

        press("Delete")

        assertTrue(deleted)
    }

    @Test
    fun theQuestionCanBeTurnedDown() = runComposeUiTest {
        var deleted = false
        setLibrary(
            initial = LibrarySelection(
                active = true,
                locations = setOf(library.first().document.location),
            ),
            onDelete = { deleted = true },
        )

        onNodeWithContentDescription("Delete from library").performClick()
        press("Cancel")

        assertTrue(!deleted)
        onNodeWithText("1 selected").assertIsDisplayed()
    }

    /** How far through it is, is the news while a bulk action runs. */
    @Test
    fun aRunningActionSaysHowFarThroughItIs() = runComposeUiTest {
        setLibrary(
            initial = LibrarySelection(active = true, locations = setOf("/a", "/b", "/c")),
            running = BulkAction(label = "Refreshing", completed = 1, total = 3),
        )

        onNodeWithText("Refreshing 1 of 3…").assertIsDisplayed()
    }
}
