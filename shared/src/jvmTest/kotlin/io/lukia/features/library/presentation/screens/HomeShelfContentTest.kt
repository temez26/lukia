package io.lukia.features.library.presentation.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.lukia.features.library.book
import io.lukia.features.library.library
import io.lukia.features.library.presentation.model.LibraryBook
import io.lukia.features.library.presentation.sessions.HomeBookActions
import io.lukia.features.library.titles
import io.lukia.shared.presentation.theme.LukiaTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** The screen a home row's title opens. */
@OptIn(ExperimentalTestApi::class)
class HomeShelfContentTest {

    @Test
    fun pressingABookOpensIt() = runComposeUiTest {
        val opened = mutableListOf<String>()

        setContent {
            LukiaTheme(darkTheme = false) {
                HomeShelfContent(
                    title = "Favorites",
                    books = library(book("Dune"), book("Solaris")),
                    actions = HomeBookActions(onOpen = { opened += listOf(it).titles() }),
                    onBack = {},
                )
            }
        }

        onNodeWithText("Favorites").assertExists()
        onNodeWithText("Solaris").performClick()

        assertEquals(listOf("Solaris"), opened)
    }

    /** Emptied while open - the last favourite un-favourited - it says so rather than going blank. */
    @Test
    fun anEmptyShelfSaysSo() = runComposeUiTest {
        setContent {
            LukiaTheme(darkTheme = false) {
                HomeShelfContent(
                    title = "Favorites",
                    books = emptyList(),
                    actions = HomeBookActions.None,
                    onBack = {},
                )
            }
        }

        onNodeWithText("No books here").assertExists()
    }

    private fun serverOnly(title: String) =
        library(book(title, onServer = true)).map { it.copy(document = it.document.copy(exists = false)) }

    private fun showing(books: List<LibraryBook>, transfers: Map<String, BookTransfer>) = @Composable {
        LukiaTheme(darkTheme = false) {
            HomeShelfContent(
                title = "Recently Added",
                books = books,
                actions = HomeBookActions.None,
                onBack = {},
                transfers = transfers,
            )
        }
    }

    /** Opening a book only on the server downloads it first, and the cover says how far it has got. */
    @Test
    fun aDownloadingBookShowsHowFarItHasGot() = runComposeUiTest {
        val books = serverOnly("Dune")
        setContent(showing(books, mapOf(books.single().location to BookTransfer.Running(0.42f))))

        onNodeWithText("42%").assertExists()
        onNodeWithContentDescription("Downloading… 42%", useUnmergedTree = true).assertExists()
    }

    @Test
    fun aFailedDownloadSaysSo() = runComposeUiTest {
        val books = serverOnly("Dune")
        setContent(showing(books, mapOf(books.single().location to BookTransfer.Failed)))

        onNodeWithText("The download did not finish.").assertExists()
    }

    /** Before anything is pressed, a book that will have to download first is marked as only on the server. */
    @Test
    fun aBookOnlyOnTheServerIsMarked() = runComposeUiTest {
        setContent(showing(serverOnly("Dune"), emptyMap()))

        onNodeWithContentDescription("On server", useUnmergedTree = true).assertExists()
    }

    @Test
    fun aBookOnTheDeviceCarriesNoDownloadMark() = runComposeUiTest {
        setContent(showing(library(book("Dune")), emptyMap()))

        onNodeWithContentDescription("On server", useUnmergedTree = true).assertDoesNotExist()
    }
}
