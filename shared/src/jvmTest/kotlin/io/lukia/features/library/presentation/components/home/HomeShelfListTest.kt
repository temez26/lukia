package io.lukia.features.library.presentation.components.home

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.lukia.core.domain.document.BookMetadata
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.domain.document.DocumentType
import io.lukia.core.domain.document.PickedDocument
import io.lukia.core.domain.reading.ReadingProgress
import io.lukia.features.library.presentation.model.HomeRow
import io.lukia.features.library.presentation.model.HomeShelf
import io.lukia.features.library.presentation.model.asLibrary
import io.lukia.features.library.presentation.sessions.HomeBookActions
import io.lukia.shared.presentation.theme.LukiaTheme
import kotlin.test.Test

/** Home's shelves: the books just opened as a short list, the rest as rows of covers. */
@OptIn(ExperimentalTestApi::class)
class HomeShelfListTest {

    private fun book(index: Int) = DocumentInfo(
        document = PickedDocument("book$index.epub", "epub", "/library/book$index.epub"),
        type = DocumentType.EPUB,
        exists = true,
        sizeBytes = 1_000L,
        metadata = BookMetadata(title = "Book $index", authors = listOf("Author $index")),
    )

    private fun ComposeUiTest.show(shelf: HomeShelf) {
        val documents = (1..5).map(::book)
        val progress = mapOf(documents.first().document.location to ReadingProgress(pageIndex = 41, pageCount = 100))
        val row = HomeRow(shelf = shelf, books = documents.asLibrary(progress))

        setContent {
            LukiaTheme(darkTheme = false) {
                HomeShelfRow(
                    row = row,
                    actions = HomeBookActions.None,
                    openMenu = null,
                    onOpenMenuChange = {},
                    onOpenShelf = {},
                    modifier = Modifier.width(400.dp),
                )
            }
        }
    }

    @Test
    fun recentlyOpenedListsItsBooksWithHowFarThrough() = runComposeUiTest {
        show(HomeShelf.RecentlyOpened)

        onNodeWithContentDescription("Recently Opened, 5 books").assertExists()
        onNodeWithText("Book 1", useUnmergedTree = true).assertExists()
        onNodeWithText("Author 1", useUnmergedTree = true).assertExists()
        onNodeWithText("42%", useUnmergedTree = true).assertExists()
        onNodeWithText("Book 3", useUnmergedTree = true).assertExists()
    }

    @Test
    fun recentlyAddedIsARowOfCovers() = runComposeUiTest {
        show(HomeShelf.RecentlyAdded)

        onNodeWithContentDescription("Recently Added, 5 books").assertExists()
        onNodeWithText("Book 1", useUnmergedTree = true).assertExists()
        onNodeWithText("Book 2", useUnmergedTree = true).assertExists()
    }

    /** Favourites are a fan of covers, naming the top one and counting the rest. */
    @Test
    fun favouritesAreAFanThatCountsTheRest() = runComposeUiTest {
        show(HomeShelf.Favorites)

        onNodeWithContentDescription("Favourites, 5 books").assertExists()
        onNodeWithText("Book 1", useUnmergedTree = true).assertExists()
        onNodeWithText("and 4 more", useUnmergedTree = true).assertExists()
    }
}
