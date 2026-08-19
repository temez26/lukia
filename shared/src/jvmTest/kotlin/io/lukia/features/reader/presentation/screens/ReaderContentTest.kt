package io.lukia.features.reader.presentation.screens

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import io.lukia.features.reader.domain.ReaderSettings
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ReaderContentTest {

    @Test
    fun theBarNamesTheBookWhereThereIsRoomForIt() = runComposeUiTest {
        setReader(pageCount = 10)

        onNodeWithText("book").assertExists()
    }

    /** Coming back to a book means coming back to the page, not to the cover. */
    @Test
    fun theBookOpensWhereItWasLeftOff() = runComposeUiTest {
        setReader(pageCount = 348, initialPage = 119)

        onNodeWithText("120 / 348").assertExists()
    }

    @Test
    fun aBookWithNoStoredPositionOpensAtTheStart() = runComposeUiTest {
        setReader(pageCount = 348, initialPage = 0)

        onNodeWithText("1 / 348").assertExists()
    }

    /** What is reported is where the reader came to rest, not what they flew past. */
    @Test
    fun theSettledPageIsReported() = runComposeUiTest {
        val settled = mutableListOf<Int>()
        setReader(pageCount = 348, onPageSettled = { settled += it })

        onNode(hasScrollToIndexAction()).performScrollToIndex(11)
        waitForIdle()

        assertEquals(11, settled.lastOrNull())
    }

    /** A shortcut nobody can find is a shortcut nobody has. */
    @Test
    fun theSettingsPanelSaysWhatTheKeysDo() = runComposeUiTest {
        setReader(pageCount = 10)

        onNodeWithContentDescription("Reading settings").performClick()

        onNodeWithText("Keyboard").assertExists()
        onNodeWithText("Turn page").assertExists()
        onNodeWithText("Space  PgDn  PgUp").assertExists()
    }

    @Test
    fun theKeyboardTurnsPages() = runComposeUiTest {
        setReader(pageCount = 348)

        onRoot().performKeyInput { pressKey(Key.PageDown) }
        waitForIdle()
        onNodeWithText("2 / 348").assertExists()

        onRoot().performKeyInput { pressKey(Key.PageDown) }
        waitForIdle()
        onNodeWithText("3 / 348").assertExists()

        onRoot().performKeyInput { pressKey(Key.PageUp) }
        waitForIdle()
        onNodeWithText("2 / 348").assertExists()
    }

    /**
     * The last page, not the last page pinned to the top of the window - a book
     * that scrolls has nothing left to scroll once its end is on screen, so what
     * End promises is that the end is reached, not what the counter then reads.
     */
    @Test
    fun theKeyboardReachesBothEndsOfTheBook() = runComposeUiTest {
        setReader(pageCount = 348)

        onRoot().performKeyInput { pressKey(Key.MoveEnd) }
        waitForIdle()
        onNodeWithContentDescription("Page 348 of 348").assertExists()

        onRoot().performKeyInput { pressKey(Key.MoveHome) }
        waitForIdle()
        onNodeWithText("1 / 348").assertExists()
    }

    /** The only way to get closer to a PDF's fixed-size type, so it has a key. */
    @Test
    fun theKeyboardChangesThePageWidth() = runComposeUiTest {
        var changed: ReaderSettings? = null
        setReader(pageCount = 10, onSettingsChange = { changed = it })

        onRoot().performKeyInput { pressKey(Key.Equals) }
        assertEquals(1.1f, changed?.pageScale)

        onRoot().performKeyInput { pressKey(Key.Minus) }
        assertEquals(0.9f, changed?.pageScale)

        onRoot().performKeyInput { pressKey(Key.Zero) }
        assertEquals(ReaderSettings.DEFAULT_PAGE_SCALE, changed?.pageScale)
    }

    @Test
    fun theKeyboardOpensBothPanels() = runComposeUiTest {
        setReader(pageCount = 348, outline = chapters)

        onRoot().performKeyInput { pressKey(Key.T) }
        waitForIdle()
        onNodeWithText("Theme").assertExists()

        onRoot().performKeyInput { pressKey(Key.C) }
        waitForIdle()
        onNodeWithText("Chapters").assertExists()
        assertEquals(0, onAllNodes(hasText("Theme")).fetchSemanticsNodes().size)
    }

    /** Escape belongs to whatever is open on top of the book before the book. */
    @Test
    fun escapeClosesAnOpenPanelRatherThanTheBook() = runComposeUiTest {
        var closed = false
        setReader(pageCount = 348, outline = chapters, onBack = { closed = true })

        onRoot().performKeyInput { pressKey(Key.T) }
        waitForIdle()
        onRoot().performKeyInput { pressKey(Key.Escape) }
        waitForIdle()

        assertEquals(false, closed)
        assertEquals(0, onAllNodes(hasText("Theme")).fetchSemanticsNodes().size)
    }

    @Test
    fun escapeLeavesTheBookWhenNothingElseIsOpen() = runComposeUiTest {
        var closed = false
        setReader(pageCount = 348, onBack = { closed = true })

        onRoot().performKeyInput { pressKey(Key.Escape) }

        assertEquals(true, closed)
    }

    /**
     * A shortcut has to show its work: a page width changed with the chrome put
     * away would look like nothing happening.
     */
    @Test
    fun aShortcutBringsTheChromeBack() = runComposeUiTest {
        setReader(pageCount = 348)

        onNodeWithContentDescription("Page 2 of 348").performClick()
        waitForIdle()
        assertEquals(
            0,
            onAllNodes(hasContentDescription("Close book")).fetchSemanticsNodes().size,
        )

        onRoot().performKeyInput { pressKey(Key.Equals) }
        waitForIdle()

        onNodeWithContentDescription("Close book").assertExists()
    }

    @Test
    fun leavingTheBookIsAlwaysReachable() = runComposeUiTest {
        var closed = false
        setReader(pageCount = 10, onBack = { closed = true })

        onNodeWithContentDescription("Close book").performClick()

        assertEquals(true, closed)
    }
}
