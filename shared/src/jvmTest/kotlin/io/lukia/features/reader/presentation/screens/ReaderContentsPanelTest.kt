package io.lukia.features.reader.presentation.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.lukia.core.domain.reading.ReadingProgress
import io.lukia.features.reader.domain.outline.OutlineEntry
import io.lukia.features.reader.domain.outline.ReaderOutline
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ReaderContentsPanelTest {

    @Test
    fun theContentsPanelOpensFromThePageIndicator() = runComposeUiTest {
        setReader(pageCount = 348, outline = chapters)

        openContents(pageCount = 348)

        onNodeWithText("Chapters").assertExists()
        onNodeWithText("The Middle").assertExists()

        // Three times: the chapter the reader is in is named in the bar, over
        // the panel's scrubber, and in the list.
        assertEquals(3, onAllNodesWithText("Introduction").fetchSemanticsNodes().size)
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
        onNodeWithContentDescription("Show sections of The Middle").performClick()

        onNodeWithContentDescription("A Section, page 130").assertExists()
    }

    /**
     * Folded but for the chapter being read: a long book's sections are in the
     * way of every other chapter, and the reader's own are the ones they want.
     */
    @Test
    fun theContentsOpenWithOnlyTheReadersChapterUnfolded() = runComposeUiTest {
        setReader(pageCount = 348, outline = chapters)

        openContents(pageCount = 348)

        assertEquals(
            0,
            onAllNodes(hasContentDescription("A Section, page 130")).fetchSemanticsNodes().size,
        )
    }

    @Test
    fun theChapterBeingReadHasItsSectionsShown() = runComposeUiTest {
        setReader(
            pageCount = 348,
            outline = chapters,
            initialPosition = ReadingProgress(pageIndex = 129, pageCount = 348),
        )

        onNode(hasContentDescription("Page 130 of 348", substring = true) and hasClickAction())
            .performClick()

        onNodeWithContentDescription("A Section, page 130").assertExists()
        onNodeWithContentDescription("Hide sections of The Middle").assertExists()
    }

    /** What the scrubber would land in, by name, and how much of it is left. */
    @Test
    fun theScrubberNamesTheChapterAndWhatIsLeftOfIt() = runComposeUiTest {
        setReader(pageCount = 348, outline = chapters)

        openContents(pageCount = 348)

        onNode(
            hasContentDescription("Introduction. Page 1 of 348", substring = true) and
                hasContentDescription("119 pages left in this chapter", substring = true),
        ).assertExists()
    }

    /**
     * A chapter the layout has not reached has no page yet. Showing the last page
     * laid out so far put the same wrong number against every later chapter.
     */
    @Test
    fun aChapterNotLaidOutYetShowsNoPage() = runComposeUiTest {
        setReader(
            pageCount = 348,
            outline = ReaderOutline.Chapters(
                listOf(
                    OutlineEntry(title = "Introduction", pageIndex = 0, level = 0),
                    OutlineEntry(title = "Later", pageIndex = 347, level = 0, placed = false),
                ),
            ),
        )

        openContents(pageCount = 348)

        onNodeWithContentDescription("Later").assertExists()
        assertEquals(
            0,
            onAllNodes(hasContentDescription("Later, page 348")).fetchSemanticsNodes().size,
        )
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

        onNodeWithContentDescription("Show sections of The Middle").assertExists()
        assertEquals(
            0,
            onAllNodes(hasContentDescription("Show sections of Introduction"))
                .fetchSemanticsNodes().size,
        )
    }

    @Test
    fun foldingAChapterHidesItsSectionsAndUnfoldingBringsThemBack() = runComposeUiTest {
        setReader(pageCount = 348, outline = chapters)

        openContents(pageCount = 348)
        onNodeWithContentDescription("Show sections of The Middle").performClick()

        onNodeWithContentDescription("A Section, page 130").assertExists()

        onNodeWithContentDescription("Hide sections of The Middle").performClick()

        assertEquals(
            0,
            onAllNodes(hasContentDescription("A Section, page 130")).fetchSemanticsNodes().size,
        )
    }

    /** The chapter itself stays reachable while its sections are folded away. */
    @Test
    fun aFoldedChapterCanStillBeGoneTo() = runComposeUiTest {
        setReader(pageCount = 348, outline = chapters)

        openContents(pageCount = 348)
        onNodeWithContentDescription("The Middle, page 120").performClick()

        onNodeWithText("120 / 348").assertExists()
    }

    @Test
    fun everySectionCanBeFoldedAtOnce() = runComposeUiTest {
        setReader(pageCount = 348, outline = chapters)

        openContents(pageCount = 348)
        onNodeWithContentDescription("Expand all").performClick()

        onNodeWithContentDescription("A Section, page 130").assertExists()

        onNodeWithContentDescription("Collapse all").performClick()

        assertEquals(
            0,
            onAllNodes(hasContentDescription("A Section, page 130")).fetchSemanticsNodes().size,
        )
    }

    /** A phone's contents rise from the bottom, and the handle puts them away. */
    @Test
    fun onAPhoneTheContentsAreASheetItsHandleCloses() = runComposeUiTest {
        setReader(pageCount = 348, outline = chapters, windowWidth = 390.dp)

        openContents(pageCount = 348)
        onNodeWithText("Chapters").assertExists()

        onNodeWithContentDescription("Close this panel").performClick()

        assertEquals(0, onAllNodes(hasText("Chapters")).fetchSemanticsNodes().size)
    }

    /** Which book this is heads the contents, as every reader's does. */
    @Test
    fun theContentsNameTheBook() = runComposeUiTest {
        setReader(pageCount = 348, outline = chapters)
        // A wide window's bar names the book already; the panel names it again.
        val before = onAllNodesWithText("book").fetchSemanticsNodes().size

        openContents(pageCount = 348)

        assertEquals(before + 1, onAllNodesWithText("book").fetchSemanticsNodes().size)
    }

    /** How long each chapter is, sections included, as Kobo shows it. */
    @Test
    fun eachChapterSaysHowLongItIs() = runComposeUiTest {
        setReader(pageCount = 348, outline = chapters)

        openContents(pageCount = 348)

        onNodeWithText("119 pages").assertExists()
        onNodeWithText("229 pages").assertExists()
    }

    /** With nothing nested there is nothing to fold, so nothing is offered. */
    @Test
    fun aFlatOutlineOffersNoFolding() = runComposeUiTest {
        setReader(
            pageCount = 348,
            outline = ReaderOutline.Chapters(
                listOf(OutlineEntry(title = "Introduction", pageIndex = 0, level = 0)),
            ),
        )

        openContents(pageCount = 348)

        assertEquals(
            0,
            onAllNodes(hasContentDescription("Expand all")).fetchSemanticsNodes().size,
        )
    }

    /**
     * How far in, which a page number on its own does not say.
     *
     * Asserted on the scrubber's own line rather than on the figure alone. The
     * reader states the same share in its bar while its chrome is showing - see
     * `PageIndicatorButton` - and "1%" on its own no longer picks out one of
     * them.
     */
    @Test
    fun theContentsPanelShowsHowFarThroughTheBookTheReaderIs() = runComposeUiTest {
        setReader(pageCount = 100, outline = chapters)

        openContents(pageCount = 100)

        onNode(hasContentDescription("Page 1 of 100, 1 percent through", substring = true))
            .assertExists()
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
            onAllNodes(hasText("This document has no chapters.")).fetchSemanticsNodes().size,
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

    /**
     * A wide window has room to say which book is open, and no other title bar
     * of its own to say it in.
     */
}
