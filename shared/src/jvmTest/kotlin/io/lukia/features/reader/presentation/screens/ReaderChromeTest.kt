package io.lukia.features.reader.presentation.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.runComposeUiTest
import io.lukia.core.presentation.UiState
import io.lukia.features.reader.domain.ReaderOutline
import io.lukia.features.reader.domain.ReaderSettings
import io.lukia.features.reader.domain.ReaderTheme
import io.lukia.features.reader.domain.ReadingWidth
import io.lukia.shared.presentation.theme.LukiaTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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

    /** Reading wants the whole screen; a tap is how the chrome comes back. */
    @Test
    fun tappingThePageHidesTheChromeAndTappingAgainRestoresIt() = runComposeUiTest {
        setReader(pageCount = 348)

        onNodeWithContentDescription("Close book").assertExists()

        onNodeWithContentDescription("Page 2 of 348").performClick()
        waitForIdle()
        assertEquals(
            0,
            onAllNodes(hasContentDescription("Close book")).fetchSemanticsNodes().size,
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
            settings = ReaderSettings(pageScale = ReadingWidth.Largest.fraction),
        )

        onNodeWithText("1 / 348").assertExists()

        onNode(hasScrollToIndexAction()).performScrollToIndex(11)

        onNodeWithText("12 / 348").assertExists()
    }

    /**
     * A PDF's type and pictures were both placed when the file was made, so the
     * controls for them have nothing to act on. They are shown and disabled
     * rather than hidden.
     */
    @Test
    fun textAndImageSettingsAreDisabledForAFixedLayoutDocument() = runComposeUiTest {
        var changed: ReaderSettings? = null
        setReader(pageCount = 10, onSettingsChange = { changed = it })

        onNodeWithContentDescription("Reading settings").performClick()

        onNode(hasContentDescription("Increase text size") and hasClickAction())
            .assertIsNotEnabled()
        onNodeWithText(
            "These apply to reflowable books - this one carries " +
                "its own fixed layout.",
        ).assertExists()
        assertNull(changed)
    }

    /** How large the pictures are is a setting somebody has to be able to find. */
    @Test
    fun theSettingsPanelOffersAnImageSize() = runComposeUiTest {
        setReader(pageCount = 10)

        onNodeWithContentDescription("Reading settings").performClick()

        onNodeWithText("Image size").assertExists()
        onNodeWithText("Fill").assertExists()
    }

    @Test
    fun theSettingsPanelOffersAnAlignment() = runComposeUiTest {
        setReader(pageCount = 10)

        onNodeWithContentDescription("Reading settings").performClick()

        onNodeWithText("Alignment").assertExists()
        onNodeWithText("Justified").assertExists()
    }
}
