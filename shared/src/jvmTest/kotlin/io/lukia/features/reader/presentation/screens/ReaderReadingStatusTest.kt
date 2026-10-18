package io.lukia.features.reader.presentation.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * How far through the book the reader is, stated in the reader's bar beside the
 * page: one bar for where the reader is and how far through, rather than a
 * second one along the bottom edge.
 */
@OptIn(ExperimentalTestApi::class)
class ReaderReadingStatusTest {

    /** A hundred pages, so the first of them is a round one percent. */
    private val pageCount = 100

    @Test
    fun theShareIsShownInTheBar() = runComposeUiTest {
        setReader(pageCount = pageCount)

        onNodeWithText("1%").assertExists()
        onNode(hasContentDescription("1 percent read", substring = true)).assertExists()
    }

    /** The page and the share are one statement in the bar, not two loose labels. */
    @Test
    fun theShareAndThePageCountAreBothShown() = runComposeUiTest {
        setReader(pageCount = pageCount)

        onNodeWithText("1 / $pageCount").assertExists()
        onNode(hasContentDescription("Page 1 of $pageCount. 1 percent read")).assertExists()
    }

    /** A share is only worth showing if it follows the reader through the book. */
    @Test
    fun theShareFollowsTheScroll() = runComposeUiTest {
        setReader(pageCount = pageCount)

        onNode(hasScrollToIndexAction()).performScrollToIndex(41)

        onNodeWithText("42%").assertExists()
    }

    @Test
    fun theShareGoesAwayWithTheBar() = runComposeUiTest {
        setReader(pageCount = pageCount)

        onNodeWithContentDescription("Page 2 of $pageCount")
            .performTouchInput { doubleClick() }
        waitForIdle()

        assertEquals(
            0,
            onAllNodes(hasContentDescription("1 percent read", substring = true)).fetchSemanticsNodes().size,
        )
    }
}
