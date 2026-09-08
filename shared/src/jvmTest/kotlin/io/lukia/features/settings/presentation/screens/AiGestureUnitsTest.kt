package io.lukia.features.settings.presentation.screens

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
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
import io.lukia.resources.settings_area_ai_translation
import io.lukia.shared.presentation.theme.LukiaTheme
import io.lukia.shared.words
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * How much of the book a gesture asks the model for, and what that row is not
 * allowed to decide.
 *
 * Whether a tap translates at all is a switch on the Translating page, beside
 * the dictionary that answers a word without any of this being set up. This row
 * only raises what a tap asks for. The two shared one nullable field once, so
 * setting this row to "off" reached across and turned that switch off - and
 * touching it at all turned the switch on. They are two settings now, and this
 * is the seam.
 *
 * The hold is not the same shape, and deliberately: translating by holding is
 * offered here and nowhere else, so its row keeps an off of its own.
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

        onNodeWithText(words(Res.string.settings_area_ai_translation)).performClick()
    }

    /**
     * One off on the page, and it belongs to the hold. The tap's off is the
     * switch under Translating.
     */
    @Test
    fun onlyTheHoldRowOffersAnOff() = runComposeUiTest {
        setPage()

        assertEquals(
            1,
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

    /** And lowering it back to the word does not stop it, either. */
    @Test
    fun norDoesLoweringItToTheWordTurnItOff() = runComposeUiTest {
        setPage(TranslationGestures(tapTranslates = true, tapUnit = PassageUnit.Sentence))

        chooseInTheTapRow(words(Res.string.passage_unit_word))

        assertEquals(
            TranslationGestures(tapTranslates = true, tapUnit = PassageUnit.Word),
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
