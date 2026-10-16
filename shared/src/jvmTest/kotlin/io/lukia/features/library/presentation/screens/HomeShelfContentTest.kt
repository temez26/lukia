package io.lukia.features.library.presentation.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.lukia.features.library.book
import io.lukia.features.library.library
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
}
