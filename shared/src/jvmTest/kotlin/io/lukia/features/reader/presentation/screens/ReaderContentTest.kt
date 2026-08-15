package io.lukia.features.reader.presentation.screens

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.lukia.core.domain.DocumentInfo
import io.lukia.core.domain.DocumentType
import io.lukia.core.domain.PickedDocument
import io.lukia.core.domain.WorkingCopy
import io.lukia.core.presentation.UiState
import io.lukia.features.reader.domain.DocumentPages
import io.lukia.features.reader.domain.PageSize
import io.lukia.features.reader.domain.ReaderDocument
import io.lukia.features.reader.domain.ReaderSettings
import io.lukia.features.reader.domain.ReaderTheme
import io.lukia.features.reader.domain.ReadingWidth
import io.lukia.shared.presentation.theme.LukiaTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class ReaderContentTest {

    @Test
    fun theToolbarShowsThePageAndTheTotal() = runComposeUiTest {
        setReader(pageCount = 348)

        onNodeWithText("1 / 348").assertExists()
    }

    /**
     * The count used to sit in a pill over the bottom of the page. Two of them
     * would be worse than the old one, so this pins that there is exactly one.
     */
    @Test
    fun thereIsOnlyOnePageIndicator() = runComposeUiTest {
        setReader(pageCount = 348)

        assertEquals(1, onAllNodes(hasText("1 / 348")).fetchSemanticsNodes().size)
        assertEquals(
            1,
            onAllNodes(hasContentDescription("Page 1 of 348")).fetchSemanticsNodes().size
        )
    }

    /** "12 / 348" is for looking at; assistive technology is given the sentence. */
    @Test
    fun thePageIndicatorReadsAsASentenceToAssistiveTechnology() = runComposeUiTest {
        setReader(pageCount = 348)

        onNodeWithContentDescription("Page 1 of 348").assertExists()
    }

    /** The count is only useful if it follows the reader through the book. */
    @Test
    fun thePageIndicatorFollowsTheScroll() = runComposeUiTest {
        setReader(pageCount = 348)

        onNode(hasScrollAction()).performScrollToIndex(11)

        onNodeWithText("12 / 348").assertExists()
        onNodeWithContentDescription("Page 12 of 348").assertExists()
    }

    /** Every theme has to be able to draw - each brings its own page filter. */
    @Test
    fun everyThemeRenders() = runComposeUiTest {
        ReaderTheme.entries.forEach { theme ->
            setReader(pageCount = 3, settings = ReaderSettings(theme = theme))

            onNodeWithText("1 / 3").assertExists()
        }
    }

    @Test
    fun aDocumentThatIsStillOpeningShowsNoPageCount() = runComposeUiTest {
        setContent {
            LukiaTheme {
                ReaderContent(
                    uiState = UiState.Loading,
                    settings = ReaderSettings(),
                    onBack = {},
                    onRetry = {},
                    onSettingsChange = {},
                    renderPage = { _, _ -> null }
                )
            }
        }

        onNodeWithContentDescription("Close book").assertExists()
        assertEquals(0, onAllNodes(hasText("/", substring = true)).fetchSemanticsNodes().size)
    }

    @Test
    fun theSettingsPanelOpensFromTheToolbar() = runComposeUiTest {
        setReader(pageCount = 10)

        onNodeWithContentDescription("Reading settings").performClick()

        onNodeWithText("Theme").assertExists()
        onNodeWithText("Reading width").assertExists()
    }

    @Test
    fun choosingAThemeReportsItImmediately() = runComposeUiTest {
        var changed: ReaderSettings? = null
        setReader(pageCount = 10, onSettingsChange = { changed = it })

        onNodeWithContentDescription("Reading settings").performClick()
        onNodeWithText("Sepia").performClick()

        assertEquals(ReaderTheme.Sepia, changed?.theme)
    }

    @Test
    fun choosingAReadingWidthReportsItImmediately() = runComposeUiTest {
        var changed: ReaderSettings? = null
        setReader(pageCount = 10, onSettingsChange = { changed = it })

        onNodeWithContentDescription("Reading settings").performClick()
        onNodeWithText("Narrow").performClick()

        assertEquals(ReadingWidth.Narrow, changed?.readingWidth)
    }

    /**
     * A PDF's type was set when the file was made, so the text controls have
     * nothing to act on. They are shown and disabled rather than hidden.
     */
    @Test
    fun textSettingsAreDisabledForAFixedLayoutDocument() = runComposeUiTest {
        var changed: ReaderSettings? = null
        setReader(pageCount = 10, onSettingsChange = { changed = it })

        onNodeWithContentDescription("Reading settings").performClick()

        onNode(hasContentDescription("Increase text size") and hasClickAction())
            .assertIsNotEnabled()
        onNodeWithText("Text settings apply to reflowable books - this one " +
            "carries its own fixed layout.").assertExists()
        assertNull(changed)
    }

    @Test
    fun leavingTheBookIsAlwaysReachable() = runComposeUiTest {
        var closed = false
        setReader(pageCount = 10, onBack = { closed = true })

        onNodeWithContentDescription("Close book").performClick()

        assertEquals(true, closed)
    }

    private fun ComposeUiTest.setReader(
        pageCount: Int,
        settings: ReaderSettings = ReaderSettings(),
        onBack: () -> Unit = {},
        onSettingsChange: (ReaderSettings) -> Unit = {}
    ) {
        setContent {
            LukiaTheme {
                ReaderContent(
                    uiState = UiState.Success(readerDocument(pageCount)),
                    settings = settings,
                    onBack = onBack,
                    onRetry = {},
                    onSettingsChange = onSettingsChange,
                    // Pages never arrive: this is about the chrome around them,
                    // and a page's own sheet is laid out before it renders.
                    renderPage = { _, _ -> null }
                )
            }
        }
    }

    private fun readerDocument(pageCount: Int) = ReaderDocument(
        info = DocumentInfo(
            document = PickedDocument(
                fileName = "book.pdf",
                extension = "pdf",
                location = "/library/book.pdf"
            ),
            type = DocumentType.PDF,
            exists = true,
            sizeBytes = 1024L
        ),
        workingCopy = WorkingCopy(
            sourceLocation = "/library/book.pdf",
            path = "/workspace/book.pdf"
        ),
        pages = FakePages(pageCount)
    )

    private class FakePages(pageCount: Int) : DocumentPages {
        override val pageSizes: List<PageSize> =
            List(pageCount) { PageSize(width = 595f, height = 842f) }

        override suspend fun renderPage(index: Int, targetWidthPx: Int): ImageBitmap? = null

        override fun close() = Unit
    }
}
