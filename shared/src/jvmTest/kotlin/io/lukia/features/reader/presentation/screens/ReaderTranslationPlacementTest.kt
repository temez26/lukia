package io.lukia.features.reader.presentation.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.lukia.features.translate.domain.BulkTranslation
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Where the translation controls are, which follows the width of the window.
 *
 * A bar built for a thumb has room for the way out, the page, and one more
 * control. The three translation ones move into the settings panel there, where
 * each is a row that says what it does rather than an unlabelled glyph with a
 * tooltip no touch device can show.
 */
@OptIn(ExperimentalTestApi::class)
class ReaderTranslationPlacementTest {

    private val everything = BulkTranslation(onScreen = true, asYouRead = true)

    @Test
    fun aPhoneKeepsThemInThePanel() = runComposeUiTest {
        setReader(translationConfigured = true, bulkTranslation = everything, width = PhoneWidth)

        assertEquals(
            0,
            onAllNodes(hasContentDescription("Translate what is on screen"))
                .fetchSemanticsNodes()
                .size,
            "the phone's bar has no room for the glyphs",
        )

        onNodeWithContentDescription("Reading settings").performClick()

        onNodeWithText("Translate this page").assertIsDisplayed()
        onNodeWithText("Translate as you read").assertIsDisplayed()
        onNodeWithText("Show translations").assertIsDisplayed()
    }

    @Test
    fun aPointedAtWindowKeepsThemInTheBar() = runComposeUiTest {
        setReader(translationConfigured = true, bulkTranslation = everything, width = DesktopWidth)

        onNodeWithContentDescription("Translate what is on screen").assertIsDisplayed()

        onNodeWithContentDescription("Reading settings").performClick()

        assertEquals(
            0,
            onAllNodes(hasText("Translate as you read")).fetchSemanticsNodes().size,
            "the panel repeated what the bar was already offering",
        )
    }

    /**
     * The panel only offers what the reader has asked to be offered, exactly as
     * the bar does. These two spend a screenful at a time - see BulkTranslation.
     */
    @Test
    fun thePanelLeavesOutWhatWasNotAskedFor() = runComposeUiTest {
        setReader(
            translationConfigured = true,
            bulkTranslation = BulkTranslation(onScreen = true, asYouRead = false),
            width = PhoneWidth,
        )

        onNodeWithContentDescription("Reading settings").performClick()

        onNodeWithText("Translate this page").assertIsDisplayed()
        assertEquals(
            0,
            onAllNodes(hasText("Translate as you read")).fetchSemanticsNodes().size,
        )
    }

    @Test
    fun nothingIsOfferedUntilThereIsSomethingToTranslateWith() = runComposeUiTest {
        setReader(translationConfigured = false, bulkTranslation = everything, width = PhoneWidth)

        onNodeWithContentDescription("Reading settings").performClick()

        assertEquals(
            0,
            onAllNodes(hasText("Show translations")).fetchSemanticsNodes().size,
            "a control that can only produce an error is worse than no control",
        )
    }
}

private val PhoneWidth = 390.dp

/** Past the compact threshold, so the bar is the one with the room. */
private val DesktopWidth = 1000.dp
