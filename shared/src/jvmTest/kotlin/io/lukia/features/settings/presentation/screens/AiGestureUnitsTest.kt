package io.lukia.features.settings.presentation.screens

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import io.lukia.core.domain.text.PassageUnit
import io.lukia.features.reader.domain.ReaderSettings
import io.lukia.features.settings.domain.AppSettings
import io.lukia.features.settings.presentation.ProviderActions
import io.lukia.features.settings.presentation.ProviderState
import io.lukia.features.settings.presentation.TranslatingActions
import io.lukia.features.translate.domain.settings.TranslationGestures
import io.lukia.features.translate.domain.settings.TranslationSettings
import io.lukia.resources.Res
import io.lukia.resources.gesture_off
import io.lukia.resources.passage_unit_paragraph
import io.lukia.resources.passage_unit_word
import io.lukia.resources.settings_ai_translation
import io.lukia.shared.presentation.theme.LukiaTheme
import io.lukia.shared.words
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * How far past the word a gesture reaches, and what these rows are not allowed
 * to decide.
 *
 * The group is named for asking past the word, so neither row offers the word:
 * off says the same thing in the group's own terms. Whether a tap translates at
 * all is a switch on the Translating page, beside the dictionary that answers a
 * word without any of this being set up - so off here means the tap goes on
 * translating the word, and not that it stops.
 *
 * The two shared one nullable field once, and setting this row to off reached
 * across and turned that switch off, while touching it at all turned the switch
 * on. They are two settings now, and this is the seam.
 */
@OptIn(ExperimentalTestApi::class)
class AiGestureUnitsTest {

    private var gestures: TranslationGestures? = null

    private fun ComposeUiTest.setPage(gestures: TranslationGestures = TranslationGestures()) {
        setContent {
            LukiaTheme(darkTheme = false) {
                SettingsBody(
                    settings = AppSettings(),
                    reader = ReaderSettings(),
                    // With the switch on: everything this test is about is
                    // behind it, and off the page shows the switch and nothing
                    // else - see `AiSwitchSection`.
                    translation = TranslationSettings()
                        .withAiTranslation(true)
                        .withGestures(gestures),
                    providerState = ProviderState(),
                    onSettingsChange = {},
                    onReaderChange = {},
                    translatingActions = TranslatingActions(
                        onGesturesChange = { this@AiGestureUnitsTest.gestures = it },
                    ),
                    providerActions = ProviderActions(),
                )
            }
        }

        onAllNodesWithText(words(Res.string.settings_ai_translation)).onFirst().performClick()
    }

    /** Neither row offers the word, which is the unit this group asks past. */
    @Test
    fun theWordIsNotOneOfTheChoices() = runComposeUiTest {
        setPage()

        assertEquals(
            0,
            onAllNodes(hasText(words(Res.string.passage_unit_word))).fetchSemanticsNodes().size,
        )
    }

    @Test
    fun bothRowsOfferAnOffInstead() = runComposeUiTest {
        setPage()

        assertEquals(
            2,
            onAllNodes(hasText(words(Res.string.gesture_off))).fetchSemanticsNodes().size,
        )
    }

    /** Raising what a tap asks for does not start it translating. */
    @Test
    fun raisingTheTapDoesNotTurnItOn() = runComposeUiTest {
        setPage(TranslationGestures(tapTranslates = false))

        chooseInTheTapRow(words(Res.string.passage_unit_paragraph))

        assertEquals(
            TranslationGestures(tapTranslates = false, tapUnit = PassageUnit.Paragraph),
            gestures,
        )
    }

    /**
     * And the tap's own off puts it back to the word rather than stopping it.
     * There is no other way back: the word is not a choice in this row, and the
     * switch on the other page is about whether a tap translates at all.
     */
    @Test
    fun theTapsOffPutsItBackToTheWordRatherThanStoppingIt() = runComposeUiTest {
        setPage(TranslationGestures(tapTranslates = true, tapUnit = PassageUnit.Sentence))

        chooseInTheTapRow(words(Res.string.gesture_off))

        assertEquals(
            TranslationGestures(tapTranslates = true, tapUnit = PassageUnit.Word),
            gestures,
        )
    }

    /** The hold's off is what it says: that gesture translates nothing. */
    @Test
    fun theHoldsOffTranslatesNothing() = runComposeUiTest {
        setPage(
            TranslationGestures(
                tapTranslates = true,
                longPress = PassageUnit.Paragraph,
                wordDetails = true,
            ),
        )

        onAllNodes(hasText(words(Res.string.gesture_off)))[1].performScrollTo().performClick()

        assertEquals(
            TranslationGestures(tapTranslates = true, longPress = null, wordDetails = true),
            gestures,
        )
    }

    /**
     * The tap is the first of the two rows. Scrolled to first: the group sits
     * under the whole service card, which is taller than the test window.
     */
    private fun ComposeUiTest.chooseInTheTapRow(unit: String) {
        onAllNodes(hasText(unit))[0].performScrollTo().performClick()
    }
}
