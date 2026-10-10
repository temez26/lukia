package io.lukia.features.reader.presentation.screens

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import io.lukia.core.presentation.UiState
import io.lukia.features.reader.domain.ReaderSettings
import io.lukia.features.reader.domain.ReaderTheme
import io.lukia.features.reader.domain.outline.ReaderOutline
import io.lukia.shared.presentation.theme.LukiaTheme
import io.lukia.shared.presentation.theme.MinTouchTarget
import kotlin.test.Test
import kotlin.test.assertEquals

/** Two taps in the same place, which is what the chrome answers to. */
@OptIn(ExperimentalTestApi::class)
private fun SemanticsNodeInteraction.doubleTap() = performTouchInput { doubleClick() }

@OptIn(ExperimentalTestApi::class)
class ReaderChromeTest {

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
                .fetchSemanticsNodes().size,
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
                    renderPage = { _, _ -> null },
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

/**
     * Reading wants the whole screen; two taps are how the chrome goes and comes
     * back. Two rather than one because a single tap belongs to the words: one
     * that lands beside them, or on a translation the reader meant to put back,
     * should not cost them the toolbar.
     */
    @Test
    fun doubleTappingThePageHidesTheChromeAndDoubleTappingAgainRestoresIt() =
        runComposeUiTest {
            setReader(pageCount = 348)

            onNodeWithContentDescription("Close book").assertExists()

            onNodeWithContentDescription("Page 2 of 348").doubleTap()
            waitForIdle()
            assertEquals(
                0,
                onAllNodes(hasContentDescription("Close book")).fetchSemanticsNodes().size,
            )

            onNodeWithContentDescription("Page 2 of 348").doubleTap()
            waitForIdle()
            onNodeWithContentDescription("Close book").assertExists()
        }

    /** The other half of the same rule: one tap leaves the toolbar where it is. */
    @Test
    fun aSingleTapOnThePageLeavesTheChromeAlone() = runComposeUiTest {
        setReader(pageCount = 348)

        onNodeWithContentDescription("Page 2 of 348").performClick()
        waitForIdle()

        onNodeWithContentDescription("Close book").assertExists()
    }

    /** A panel hangs off the toolbar, so the toolbar cannot leave underneath it. */
    @Test
    fun theChromeStaysWhileAPanelIsOpen() = runComposeUiTest {
        setReader(pageCount = 348, outline = chapters)

        openContents(pageCount = 348)
        onNodeWithContentDescription("Page 2 of 348").doubleTap()
        waitForIdle()

        onNodeWithContentDescription("Close book").assertExists()
    }

    /**
     * That the page indicator is drawn as a label and pressed as a control.
     *
     * Held to a control's height, its fill was a 44dp slab around one 13sp line
     * - most of the toolbar tinted grey. The fill is drawn to its own label now,
     * and this is the half that could go wrong: what got smaller is the drawing,
     * not what a thumb has to hit.
     *
     * Pressed above the label rather than on it, where the fill used to reach
     * and where a thumb aiming at the middle of the bar still lands.
     */
    @Test
    fun thePageIndicatorIsPressedWellPastWhatItDraws() = runComposeUiTest {
        setContent {
            LukiaTheme(touch = true) {
                ReaderContent(
                    uiState = UiState.Success(readerDocument(348)),
                    settings = ReaderSettings(),
                    outline = chapters,
                    onBack = {},
                    onRetry = {},
                    onSettingsChange = {},
                    renderPage = { _, _ -> null },
                )
            }
        }

        val indicator = onNode(
            hasContentDescription("Page 1 of 348", substring = true) and hasClickAction(),
        )

        indicator.assertHeightIsAtLeast(MinTouchTarget)

        indicator.performTouchInput {
            click(Offset(width / 2f, height / 2f - AbovePress))
        }
        waitForIdle()

        onNodeWithText("Chapters").assertExists()
    }
}

/**
 * How far above the middle of the drawn label the press lands: past its top
 * edge, and inside the target reserved around it.
 */
private const val AbovePress = 16f
