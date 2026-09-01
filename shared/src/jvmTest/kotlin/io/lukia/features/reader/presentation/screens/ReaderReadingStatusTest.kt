package io.lukia.features.reader.presentation.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The reading status along the bottom edge: when it is there, and what it says.
 *
 * It belongs to the chrome rather than standing in for it. Putting the toolbar
 * away is a reader asking for the page and nothing else, and a line and a figure
 * left burning along the bottom would be two things still asking to be looked
 * at.
 */
@OptIn(ExperimentalTestApi::class)
class ReaderReadingStatusTest {

    /** A hundred pages, so the first of them is a round one percent. */
    private val pageCount = 100

    @Test
    fun theShareIsShownWhileTheToolbarIs() = runComposeUiTest {
        setReader(pageCount = pageCount)

        onNodeWithText("1%").assertExists()
        onNodeWithContentDescription("1 percent read").assertExists()
    }

    /**
     * The bar is a scale, not a stripe. Without the zero at its start a
     * part-filled track says "some" and the figure says how much without saying
     * of what.
     */
    @Test
    fun theBarIsAnchoredAtTheStartOfTheBook() = runComposeUiTest {
        setReader(pageCount = pageCount)

        // Both ends of one bar: the merged node carries the zero and the share
        // together, which is the arrangement rather than two loose labels that
        // happen to be on screen.
        onNode(hasText("0") and hasText("1%")).assertExists()
    }

    @Test
    fun theShareGoesAwayWithTheToolbar() = runComposeUiTest {
        setReader(pageCount = pageCount)

        onNodeWithContentDescription("Page 2 of $pageCount")
            .performTouchInput { doubleClick() }
        waitForIdle()

        assertEquals(
            0,
            onAllNodes(hasContentDescription("1 percent read")).fetchSemanticsNodes().size,
            "The share should leave with the chrome rather than stand in for it",
        )
    }

    /** And comes back with it, so the two are one piece of chrome. */
    @Test
    fun theShareComesBackWithTheToolbar() = runComposeUiTest {
        setReader(pageCount = pageCount)

        onNodeWithContentDescription("Page 2 of $pageCount")
            .performTouchInput { doubleClick() }
        waitForIdle()

        onNodeWithContentDescription("Page 2 of $pageCount")
            .performTouchInput { doubleClick() }
        waitForIdle()

        onNodeWithContentDescription("1 percent read").assertExists()
    }

    /** A share is only worth showing if it follows the reader through the book. */
    @Test
    fun theShareFollowsTheScroll() = runComposeUiTest {
        setReader(pageCount = pageCount)

        onNode(hasScrollToIndexAction()).performScrollToIndex(41)

        onNodeWithText("42%").assertExists()
        onNodeWithContentDescription("42 percent read").assertExists()
    }

    /**
     * The toolbar answers where the reader is - "12 / 348" - and this answers how
     * much is left. Both are on screen at once, so neither may be the only way to
     * learn the other.
     */
    @Test
    fun theShareAndThePageCountAreBothShown() = runComposeUiTest {
        setReader(pageCount = pageCount)

        onNodeWithText("1 / $pageCount").assertExists()
        onNodeWithText("1%").assertExists()
    }
}
