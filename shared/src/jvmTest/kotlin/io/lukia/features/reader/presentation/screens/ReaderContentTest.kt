package io.lukia.features.reader.presentation.screens

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
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
import io.lukia.features.reader.domain.OutlineEntry
import io.lukia.features.reader.domain.PageSize
import io.lukia.features.reader.domain.ReaderDocument
import io.lukia.features.reader.domain.ReaderOutline
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
            onAllNodes(hasContentDescription("Page 1 of 348") and hasClickAction())
                .fetchSemanticsNodes().size
        )
    }

    /** "12 / 348" is for looking at; assistive technology is given the sentence. */
    @Test
    fun thePageIndicatorReadsAsASentenceToAssistiveTechnology() = runComposeUiTest {
        setReader(pageCount = 348)

        onNode(hasContentDescription("Page 1 of 348") and hasClickAction()).assertExists()
    }

    /** The count is only useful if it follows the reader through the book. */
    @Test
    fun thePageIndicatorFollowsTheScroll() = runComposeUiTest {
        setReader(pageCount = 348)

        onNode(hasScrollToIndexAction()).performScrollToIndex(11)

        onNodeWithText("12 / 348").assertExists()
        onNode(hasContentDescription("Page 12 of 348") and hasClickAction()).assertExists()
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
                    outline = ReaderOutline.Loading,
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

    /**
     * A page is a picture with no text behind it, so its own description is the
     * only thing a screen reader has to go on. Page two is only ever a page -
     * the indicator is still showing page one - so finding it proves the
     * document itself is reachable, not just the chrome.
     */
    @Test
    fun theDocumentItselfIsReachableByAssistiveTechnology() = runComposeUiTest {
        setReader(pageCount = 348)

        onNodeWithContentDescription("Page 2 of 348").assertExists()
    }

    /** Reading wants the whole screen; a tap is how the chrome comes back. */
    @Test
    fun tappingThePageHidesTheChromeAndTappingAgainRestoresIt() = runComposeUiTest {
        setReader(pageCount = 348)

        onNodeWithContentDescription("Close book").assertExists()

        onNodeWithContentDescription("Page 2 of 348").performClick()
        waitForIdle()
        assertEquals(
            0,
            onAllNodes(hasContentDescription("Close book")).fetchSemanticsNodes().size
        )

        onNodeWithContentDescription("Page 2 of 348").performClick()
        waitForIdle()
        onNodeWithContentDescription("Close book").assertExists()
    }

    /** A panel hangs off the toolbar, so the toolbar cannot leave underneath it. */
    @Test
    fun theChromeStaysWhileAPanelIsOpen() = runComposeUiTest {
        setReader(pageCount = 348, outline = chapters)

        openContents(pageCount = 348)
        onNodeWithContentDescription("Page 2 of 348").performClick()
        waitForIdle()

        onNodeWithContentDescription("Close book").assertExists()
    }

    @Test
    fun theSettingsPanelOpensFromTheToolbar() = runComposeUiTest {
        setReader(pageCount = 10)

        onNodeWithContentDescription("Reading settings").performClick()

        onNodeWithText("Theme").assertExists()
        onNodeWithText("Page width").assertExists()
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
        onNodeWithText("75%").performClick()

        assertEquals(ReadingWidth.Narrow.fraction, changed?.pageScale)
    }

    /**
     * The larger widths are the only way to get closer to a PDF's fixed-size
     * type, so they have to be reachable from the panel.
     */
    @Test
    fun theLargerPageWidthsAreOffered() = runComposeUiTest {
        var changed: ReaderSettings? = null
        setReader(pageCount = 10, onSettingsChange = { changed = it })

        onNodeWithContentDescription("Reading settings").performClick()
        onNodeWithText("150%").performClick()

        assertEquals(ReadingWidth.Largest.fraction, changed?.pageScale)
    }

    /** A page wider than the window still lays out, and still turns pages. */
    @Test
    fun theBookStillReadsAtTheLargestPageWidth() = runComposeUiTest {
        setReader(
            pageCount = 348,
            settings = ReaderSettings(pageScale = ReadingWidth.Largest.fraction)
        )

        onNodeWithText("1 / 348").assertExists()

        onNode(hasScrollToIndexAction()).performScrollToIndex(11)

        onNodeWithText("12 / 348").assertExists()
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
    fun theContentsPanelOpensFromThePageIndicator() = runComposeUiTest {
        setReader(pageCount = 348, outline = chapters)

        openContents(pageCount = 348)

        onNodeWithText("Chapters").assertExists()
        onNodeWithText("Introduction").assertExists()
        onNodeWithText("The Middle").assertExists()
    }

    /** Tapping a chapter is the whole point: it has to move the book. */
    @Test
    fun choosingAChapterGoesToItsPage() = runComposeUiTest {
        setReader(pageCount = 348, outline = chapters)

        openContents(pageCount = 348)
        onNodeWithContentDescription("The Middle, page 120").performClick()

        onNodeWithText("120 / 348").assertExists()
    }

    /** And the panel gets out of the way once it has done its job. */
    @Test
    fun choosingAChapterClosesThePanel() = runComposeUiTest {
        setReader(pageCount = 348, outline = chapters)

        openContents(pageCount = 348)
        onNodeWithContentDescription("The Middle, page 120").performClick()

        assertEquals(0, onAllNodes(hasText("Chapters")).fetchSemanticsNodes().size)
    }

    @Test
    fun nestedSectionsAreListedUnderTheirChapter() = runComposeUiTest {
        setReader(pageCount = 348, outline = chapters)

        openContents(pageCount = 348)

        onNodeWithContentDescription("A Section, page 130").assertExists()
    }

    /**
     * A section reads as belonging to the chapter above it only if that chapter
     * can be seen to hold it - so the chapter with sections under it carries the
     * control that folds them, and the one without carries none.
     */
    @Test
    fun onlyAChapterWithSectionsOffersToFoldThem() = runComposeUiTest {
        setReader(pageCount = 348, outline = chapters)

        openContents(pageCount = 348)

        onNodeWithContentDescription("Hide sections of The Middle").assertExists()
        assertEquals(
            0,
            onAllNodes(hasContentDescription("Hide sections of Introduction"))
                .fetchSemanticsNodes().size
        )
    }

    @Test
    fun foldingAChapterHidesItsSectionsAndUnfoldingBringsThemBack() = runComposeUiTest {
        setReader(pageCount = 348, outline = chapters)

        openContents(pageCount = 348)
        onNodeWithContentDescription("Hide sections of The Middle").performClick()

        assertEquals(
            0,
            onAllNodes(hasContentDescription("A Section, page 130")).fetchSemanticsNodes().size
        )

        onNodeWithContentDescription("Show sections of The Middle").performClick()

        onNodeWithContentDescription("A Section, page 130").assertExists()
    }

    /** The chapter itself stays reachable while its sections are folded away. */
    @Test
    fun aFoldedChapterCanStillBeGoneTo() = runComposeUiTest {
        setReader(pageCount = 348, outline = chapters)

        openContents(pageCount = 348)
        onNodeWithContentDescription("Hide sections of The Middle").performClick()
        onNodeWithContentDescription("The Middle, page 120").performClick()

        onNodeWithText("120 / 348").assertExists()
    }

    @Test
    fun everySectionCanBeFoldedAtOnce() = runComposeUiTest {
        setReader(pageCount = 348, outline = chapters)

        openContents(pageCount = 348)
        onNodeWithContentDescription("Collapse all").performClick()

        assertEquals(
            0,
            onAllNodes(hasContentDescription("A Section, page 130")).fetchSemanticsNodes().size
        )

        onNodeWithContentDescription("Expand all").performClick()

        onNodeWithContentDescription("A Section, page 130").assertExists()
    }

    /** With nothing nested there is nothing to fold, so nothing is offered. */
    @Test
    fun aFlatOutlineOffersNoFolding() = runComposeUiTest {
        setReader(
            pageCount = 348,
            outline = ReaderOutline.Chapters(
                listOf(OutlineEntry(title = "Introduction", pageIndex = 0, level = 0))
            )
        )

        openContents(pageCount = 348)

        assertEquals(
            0,
            onAllNodes(hasContentDescription("Collapse all")).fetchSemanticsNodes().size
        )
    }

    /** How far in, which a page number on its own does not say. */
    @Test
    fun theContentsPanelShowsHowFarThroughTheBookTheReaderIs() = runComposeUiTest {
        setReader(pageCount = 100, outline = chapters)

        openContents(pageCount = 100)

        onNodeWithText("1%").assertExists()
    }

    @Test
    fun aDocumentWithNoChaptersSaysSo() = runComposeUiTest {
        setReader(pageCount = 10, outline = ReaderOutline.Chapters(emptyList()))

        openContents(pageCount = 10)

        onNodeWithText("This document has no chapters.").assertExists()
    }

    /**
     * Android's PDF renderer exposes no outline at all, so the reader cannot
     * tell whether a book has chapters - and must not claim it has none.
     */
    @Test
    fun aPlatformThatCannotReadChaptersSaysThatInstead() = runComposeUiTest {
        setReader(pageCount = 10, outline = ReaderOutline.Unavailable)

        openContents(pageCount = 10)

        onNodeWithText("Chapters could not be read from this document.").assertExists()
        assertEquals(
            0,
            onAllNodes(hasText("This document has no chapters.")).fetchSemanticsNodes().size
        )
    }

    @Test
    fun theScrubberReportsWhereItWouldGo() = runComposeUiTest {
        setReader(pageCount = 348, outline = chapters)

        openContents(pageCount = 348)

        onNode(hasContentDescription("Go to page. Page 1 of 348")).assertExists()
    }

    /** Opening one panel puts the other away rather than stacking them. */
    @Test
    fun onlyOnePanelIsOpenAtATime() = runComposeUiTest {
        setReader(pageCount = 348, outline = chapters)

        openContents(pageCount = 348)
        onNodeWithContentDescription("Reading settings").performClick()

        onNodeWithText("Theme").assertExists()
        assertEquals(0, onAllNodes(hasText("Chapters")).fetchSemanticsNodes().size)
    }

    @Test
    fun leavingTheBookIsAlwaysReachable() = runComposeUiTest {
        var closed = false
        setReader(pageCount = 10, onBack = { closed = true })

        onNodeWithContentDescription("Close book").performClick()

        assertEquals(true, closed)
    }

    /** The indicator is the pressable one; the page behind says the same thing. */
    private fun ComposeUiTest.openContents(pageCount: Int) {
        onNode(hasContentDescription("Page 1 of $pageCount") and hasClickAction()).performClick()
    }

    private val chapters = ReaderOutline.Chapters(
        listOf(
            OutlineEntry(title = "Introduction", pageIndex = 0, level = 0),
            OutlineEntry(title = "The Middle", pageIndex = 119, level = 0),
            OutlineEntry(title = "A Section", pageIndex = 129, level = 1)
        )
    )

    private fun ComposeUiTest.setReader(
        pageCount: Int,
        outline: ReaderOutline = ReaderOutline.Chapters(emptyList()),
        settings: ReaderSettings = ReaderSettings(),
        onBack: () -> Unit = {},
        onSettingsChange: (ReaderSettings) -> Unit = {}
    ) {
        setContent {
            LukiaTheme {
                ReaderContent(
                    uiState = UiState.Success(readerDocument(pageCount)),
                    settings = settings,
                    outline = outline,
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

        override val outline: List<OutlineEntry> = emptyList()

        // Wide and short, so that more than one page fits the test window and
        // the pages either side of the first are composed. Real pages are
        // taller; nothing here depends on their proportions.
        override val pageSizes: List<PageSize> =
            List(pageCount) { PageSize(width = 800f, height = 300f) }

        override suspend fun renderPage(index: Int, targetWidthPx: Int): ImageBitmap? = null

        override fun close() = Unit
    }
}
