@file:OptIn(ExperimentalTestApi::class)

package io.lukia.features.reader.presentation.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.runComposeUiTest
import io.lukia.core.domain.reading.ReadingProgress
import io.lukia.core.presentation.UiState
import io.lukia.features.reader.domain.ReaderSettings
import io.lukia.features.reader.domain.outline.ReaderOutline
import io.lukia.shared.presentation.theme.LukiaTheme
import kotlin.test.Test

private const val PAGE_COUNT = 348

/**
 * That opening a book where it was left off happens once, rather than every time
 * the arrangement that does it is set up again.
 *
 * Where the book opened is settled once per opening and never moved off that
 * page - see `ReaderViewModel.initialPosition` - so it is an instruction to
 * carry out, not a position to follow. Anything that starts the effect again
 * hands it the same instruction, and carrying it out a second time takes the
 * reader back to where they were an hour ago.
 *
 * The case that matters most is a turned device, which takes the composition
 * down and leaves the ViewModel standing. That one cannot be driven from here -
 * `StateRestorationTester.emulateSaveAndRestore` is not implemented for skiko,
 * so it wants a device to check - but a restarted effect is the whole of the
 * mechanism either way, and a stored position arriving late is a second real
 * way to reach it.
 */
class ReaderOpeningPositionTest {

    private val stored = ReadingProgress(pageIndex = 119, pageCount = PAGE_COUNT)

    /**
     * The documented reason the opening scroll is a scroll rather than the
     * list's starting index: the file has to be opened before its length is
     * known, so the position arrives after the list does. A book nobody has
     * touched still goes where it was left off.
     */
    @Test
    fun aPositionArrivingLateOpensAnUntouchedBookWhereItWasLeftOff() = runComposeUiTest {
        var position by mutableStateOf<ReadingProgress?>(null)

        setReaderReading { position }

        onNodeWithText("1 / $PAGE_COUNT").assertIsDisplayed()

        position = stored
        waitForIdle()

        onNodeWithText("120 / $PAGE_COUNT").assertIsDisplayed()
    }

    /**
     * And the same instruction reaching a reader who has already moved leaves
     * them where they are. A list that is somewhere has either been scrolled by
     * the reader or has restored its own position across a turned device, and
     * both are a better answer than the page this opening began at.
     */
    @Test
    fun theOpeningScrollDoesNotMoveAReaderWhoHasAlreadyGoneSomewhere() = runComposeUiTest {
        var position by mutableStateOf<ReadingProgress?>(null)

        setReaderReading { position }

        scrollTo(200)
        onNodeWithText("201 / $PAGE_COUNT").assertIsDisplayed()

        position = stored
        waitForIdle()

        onNodeWithText("201 / $PAGE_COUNT").assertIsDisplayed()
    }
}

/**
 * A reader whose opening position is read from inside the composition, so that
 * a test can hand it over part way through the way the ViewModel does.
 *
 * Set here rather than through `setReader`, which takes the position as a value
 * and so settles it before anything is composed.
 */
private fun ComposeUiTest.setReaderReading(position: () -> ReadingProgress?) {
    setContent {
        LukiaTheme {
            ReaderContent(
                uiState = UiState.Success(readerDocument(PAGE_COUNT)),
                settings = ReaderSettings(),
                outline = ReaderOutline.Chapters(emptyList()),
                initialPosition = position(),
                onBack = {},
                onRetry = {},
                onSettingsChange = {},
                renderPage = { _, _ -> null },
            )
        }
    }
}

/** The book's own list, which is the one node in the reader that scrolls by index. */
private fun ComposeUiTest.scrollTo(index: Int) {
    onNode(hasScrollToIndexAction()).performScrollToIndex(index)
    waitForIdle()
}
