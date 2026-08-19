package io.lukia.features.reader.presentation.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.lukia.features.reader.domain.OutlineEntry
import io.lukia.features.reader.domain.ReaderOutline
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ReaderContentsPanelTest {

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
                .fetchSemanticsNodes().size,
        )
    }

    @Test
    fun foldingAChapterHidesItsSectionsAndUnfoldingBringsThemBack() = runComposeUiTest {
        setReader(pageCount = 348, outline = chapters)

        openContents(pageCount = 348)
        onNodeWithContentDescription("Hide sections of The Middle").performClick()

        assertEquals(
            0,
            onAllNodes(hasContentDescription("A Section, page 130")).fetchSemanticsNodes().size,
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
            onAllNodes(hasContentDescription("A Section, page 130")).fetchSemanticsNodes().size,
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
                listOf(OutlineEntry(title = "Introduction", pageIndex = 0, level = 0)),
            ),
        )

        openContents(pageCount = 348)

        assertEquals(
            0,
            onAllNodes(hasContentDescription("Collapse all")).fetchSemanticsNodes().size,
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
