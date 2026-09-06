package io.lukia.features.reader.presentation.screens.translation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import io.lukia.core.domain.text.PassageUnit
import io.lukia.features.translate.domain.BulkTranslation
import io.lukia.features.translate.domain.settings.TranslationGestures
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The translation controls in the reader's bar, which are only offered once
 * the reader has asked for them in Settings.
 */
@OptIn(ExperimentalTestApi::class)
class ReaderTranslationControlsTest {

    @Test
    fun aTapTurnedOffStillReachesThePage() = runComposeUiTest {
        setReader(gestures = TranslationGestures(tap = null, longPress = PassageUnit.Word))

        // The chrome is what a tap on the page has always reached, so it is what
        // proves the tap arrived at all.
        onNodeWithContentDescription("Reading settings").assertExists()

        onNodeWithText(opening, substring = true).doubleClickFirstWords()
        waitForIdle()

        assertEquals(
            0,
            onAllNodes(hasContentDescription("Reading settings")).fetchSemanticsNodes().size,
            "a tap set to nothing reached nothing at all",
        )
    }

    /**
     * A control for something the reader has not asked to be offered is not in
     * the bar at all. These two spend a screenful at a time - see
     * BulkTranslation - and a button that can do that is a button somebody
     * presses to find out what it does.
     */
    @Test
    fun theExpensiveControlsAreNotThereUntilTheyAreAskedFor() = runComposeUiTest {
        setReader(translationConfigured = true)

        assertEquals(
            0,
            onAllNodes(hasContentDescription("Translate what is on screen"))
                .fetchSemanticsNodes()
                .size,
        )
    }

    @Test
    fun aControlAskedForIsInTheBar() = runComposeUiTest {
        setReader(
            translationConfigured = true,
            bulkTranslation = BulkTranslation(onScreen = true, asYouRead = false),
        )

        onNodeWithContentDescription("Translate what is on screen").assertExists()
        // And only the one that was asked for.
        assertEquals(
            0,
            onAllNodes(hasContentDescription("Keep translating as you read"))
                .fetchSemanticsNodes()
                .size,
        )
    }

    /**
     * A tap lands where it is aimed. The node is the whole paragraph, so its
     * centre is whichever sentence happens to fall in the middle of it - the
     * first few pixels are always the first sentence.
     */
}
