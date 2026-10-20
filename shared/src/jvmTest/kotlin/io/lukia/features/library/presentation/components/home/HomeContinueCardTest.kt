package io.lukia.features.library.presentation.components.home

import androidx.compose.foundation.layout.Column
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
import io.lukia.features.library.presentation.model.asLibrary
import io.lukia.features.library.presentation.sessions.HomeBookActions
import io.lukia.shared.presentation.theme.LukiaTheme
import kotlin.test.Test

/** The book being read at the top of Home: what it shows, and what a screen reader hears. */
@OptIn(ExperimentalTestApi::class)
class HomeContinueCardTest {

    private fun book(metadata: BookMetadata) = DocumentInfo(
        document = PickedDocument("piranesi.epub", "epub", "/library/piranesi.epub"),
        type = DocumentType.EPUB,
        exists = true,
        sizeBytes = 1_200_000L,
        metadata = metadata,
    )

    private fun ComposeUiTest.show(document: DocumentInfo) {
        val progress = mapOf(document.document.location to ReadingProgress(pageIndex = 125, pageCount = 300))
        val book = listOf(document).asLibrary(progress).single()

        setContent {
            LukiaTheme(darkTheme = false) {
                // As Home sets them: the book's row, and the facts about its file under it.
                Column(modifier = Modifier.width(400.dp)) {
                    HomeContinueCard(
                        book = book,
                        actions = HomeBookActions.None,
                        menuExpanded = false,
                        onMenuExpandedChange = {},
                    )
                    ContinueFacts(document = book.document)
                }
            }
        }
    }

    private val piranesi = BookMetadata(
        title = "Piranesi",
        authors = listOf("Susanna Clarke"),
        language = "en",
        published = "2020-09-15",
        description = "The Beauty of the House is immeasurable.",
    )

    @Test
    fun theProgressIsARingAndAShortPageCount() = runComposeUiTest {
        show(book(piranesi))

        onNodeWithText("42%", useUnmergedTree = true).assertExists()
        onNodeWithText("126/300", useUnmergedTree = true).assertExists()
        onNodeWithContentDescription("42%, Page 126 of 300, 174 pages left").assertExists()
    }

    /** The cover says what the book is; a screen reader is told it in words. */
    @Test
    fun theCoverNamesTheBookAndNothingElseDoes() = runComposeUiTest {
        show(book(piranesi))

        onNodeWithText("Piranesi").assertDoesNotExist()
        onNodeWithText("Susanna Clarke").assertDoesNotExist()
        onNodeWithContentDescription("Piranesi by Susanna Clarke").assertExists()
    }

    /** The blurb is for the book's own page; this card only reminds. */
    @Test
    fun theDescriptionIsNotShown() = runComposeUiTest {
        show(book(piranesi))

        onNodeWithText("The Beauty of the House is immeasurable.").assertDoesNotExist()
    }

    @Test
    fun theFactsAreShortOnScreenAndWholeWhenReadOut() = runComposeUiTest {
        show(book(piranesi))

        onNodeWithText("EN", useUnmergedTree = true).assertExists()
        onNodeWithText("2020", useUnmergedTree = true).assertExists()
        onNodeWithContentDescription("Format: EPUB").assertExists()
        onNodeWithContentDescription("Language: English").assertExists()
        onNodeWithContentDescription("Published: 2020").assertExists()
    }

    @Test
    fun aFactTheBookDoesNotStateIsLeftOut() = runComposeUiTest {
        show(book(BookMetadata(title = "Piranesi")))

        onNodeWithContentDescription("Format: EPUB").assertExists()
        onNodeWithContentDescription("Language:", substring = true).assertDoesNotExist()
        onNodeWithContentDescription("Published:", substring = true).assertDoesNotExist()
    }

    /** A language tag with nothing before its region - "-US" - names no language, so none is shown. */
    @Test
    fun aLanguageTagWithNoLanguageInItIsLeftOut() = runComposeUiTest {
        show(book(BookMetadata(title = "Piranesi", language = "-US")))

        onNodeWithContentDescription("Language:", substring = true).assertDoesNotExist()
    }
}
