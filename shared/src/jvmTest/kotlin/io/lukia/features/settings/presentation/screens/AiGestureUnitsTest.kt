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
import io.lukia.resources.passage_unit_paragraph
import io.lukia.resources.passage_unit_word
import io.lukia.resources.settings_area_ai_translation
import io.lukia.shared.presentation.theme.LukiaTheme
import io.lukia.shared.words
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * How much of the book a gesture may ask the model for, which is everything
 * larger than a word and nothing else.
 *
 * The word is left out because it is the one unit a dictionary has an entry for,
 * and whether a tap translates it is a switch on the Translating page beside
 * that dictionary. Offered here as well, it would be a second control writing
 * the same setting - and a reader who turned the tap off with the switch would
 * find it back on from a row they never touched.
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
                    translation = TranslationSettings().withGestures(gestures),
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

    @Test
    fun theWordIsNotOneOfTheChoices() = runComposeUiTest {
        setPage()

        assertEquals(
            0,
            onAllNodes(hasText(words(Res.string.passage_unit_word))).fetchSemanticsNodes().size,
            "a unit the dictionary on the other page already answers",
        )
    }

    /**
     * A tap set to the word therefore matches no segment here, and the row still
     * works: choosing the paragraph raises that same tap.
     */
    @Test
    fun aTapSetToTheWordCanStillBeRaisedFromHere() = runComposeUiTest {
        setPage(TranslationGestures(tap = PassageUnit.Word))

        // Scrolled to first: the group sits under the whole service card, which
        // is taller than the test window.
        onAllNodes(hasText(words(Res.string.passage_unit_paragraph)))[0]
            .performScrollTo()
            .performClick()

        assertEquals(TranslationGestures(tap = PassageUnit.Paragraph), gestures)
    }
}
