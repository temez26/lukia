package io.lukia.features.reader.presentation.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.runComposeUiTest
import io.lukia.core.domain.reading.PdfLayout
import io.lukia.core.presentation.UiState
import io.lukia.features.reader.domain.ReaderSettings
import io.lukia.features.reader.domain.ReaderTheme
import io.lukia.features.reader.domain.ReadingWidth
import io.lukia.features.reader.domain.text.TextExtraction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The panel behind the toolbar's settings button: what it offers, and what it
 * reports the moment something is chosen.
 *
 * Split from `ReaderChromeTest`, which keeps the chrome itself - the toolbar,
 * the page indicator and the taps that put both away. The panel hangs off that
 * chrome but is a different question: not whether the controls are reachable,
 * but whether each one is offered to the right document and answered without a
 * round trip through the reader.
 *
 * What a fixed-layout PDF is and is not asked is the part worth having here.
 * A setting that does nothing to the document in front of you is worse than one
 * that is missing, so those are disabled rather than hidden, and the layout
 * choice has to survive the conversion it starts.
 */
@OptIn(ExperimentalTestApi::class)
class ReaderSettingsPanelTest {
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
        setReader(
            pageCount = 10,
            layout = ReaderLayoutUi(offered = true),
            onSettingsChange = { changed = it },
        )

        onNodeWithContentDescription("Reading settings").performClick()
        onNodeWithText("Sepia").performClick()

        assertEquals(ReaderTheme.Sepia, changed?.theme)
    }

    @Test
    fun choosingAReadingWidthReportsItImmediately() = runComposeUiTest {
        var changed: ReaderSettings? = null
        setReader(
            pageCount = 10,
            layout = ReaderLayoutUi(offered = true),
            onSettingsChange = { changed = it },
        )

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
        setReader(
            pageCount = 10,
            layout = ReaderLayoutUi(offered = true),
            onSettingsChange = { changed = it },
        )

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
        setReader(
            pageCount = 10,
            layout = ReaderLayoutUi(offered = true),
            onSettingsChange = { changed = it },
        )

        onNodeWithContentDescription("Reading settings").performClick()

        onNode(hasContentDescription("Increase text size") and hasClickAction())
            .assertIsNotEnabled()
        onNodeWithText(
            "These apply to reflowable books - this one carries " +
                "its own fixed layout. Read it as text to use them.",
        ).assertExists()
        assertNull(changed)
    }

    /**
     * A PDF can be read as its own pages or as text this app sets, and the
     * settings panel is where that is asked for.
     */
    @Test
    fun theSettingsPanelOffersToReadAPdfAsText() = runComposeUiTest {
        var chosen: PdfLayout? = null
        setReader(
            pageCount = 10,
            layout = ReaderLayoutUi(offered = true, onLayoutChange = { chosen = it }),
        )

        onNodeWithContentDescription("Reading settings").performClick()

        onNodeWithText("Layout").assertExists()
        onNodeWithText("Text").performClick()

        assertEquals(PdfLayout.Text, chosen)
    }

    /**
     * The control has to survive the wait it causes. Asking for a long PDF as
     * text is seconds of conversion with no open book behind it, and that is
     * exactly when somebody watching the page count climb decides they would
     * rather have the pages back - so the way back cannot be the thing that
     * disappears.
     */
    @Test
    fun theLayoutChoiceIsStillThereWhileTheBookIsBeingConverted() = runComposeUiTest {
        var chosen: PdfLayout? = null
        setReader(
            pageCount = 10,
            uiState = UiState.Loading,
            layout = ReaderLayoutUi(
                layout = PdfLayout.Text,
                offered = true,
                converting = TextExtraction(pagesRead = 12, pageCount = 600),
                onLayoutChange = { chosen = it },
            ),
        )

        onNodeWithContentDescription("Reading settings").performClick()

        onNodeWithText("Layout").assertExists()
        onNodeWithText("Original").performClick()

        assertEquals(PdfLayout.Original, chosen)
    }

    /** An EPUB was never asked which way to be read, so it is not asked here. */
    @Test
    fun aBookThatTakesNoLayoutChoiceIsNotOfferedOne() = runComposeUiTest {
        setReader(pageCount = 10, layout = ReaderLayoutUi(offered = false))

        onNodeWithContentDescription("Reading settings").performClick()

        onNodeWithText("Layout").assertDoesNotExist()
    }

    /**
     * A scan has no words in the file to re-set. The reader gets the pages, and
     * has to be told why - otherwise the setting looks broken.
     */
    @Test
    fun aPdfWithNoTextToReSetSaysSo() = runComposeUiTest {
        setReader(pageCount = 10, layout = ReaderLayoutUi(noTextLayer = true))

        onNodeWithText(
            "This PDF has no text in it to re-set - its pages are pictures. " +
                "Showing it as pages.",
        ).assertExists()
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
