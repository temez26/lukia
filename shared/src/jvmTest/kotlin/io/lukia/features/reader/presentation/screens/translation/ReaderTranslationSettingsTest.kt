package io.lukia.features.reader.presentation.screens.translation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.lukia.core.domain.text.PassageUnit
import io.lukia.features.translate.domain.settings.TargetLanguage
import io.lukia.features.translate.domain.settings.TranslationGestures
import io.lukia.resources.Res
import io.lukia.resources.passage_unit_paragraph
import io.lukia.shared.words
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The settings translating has in the reader's own panel: the language, and what
 * a thumb on the page asks for.
 *
 * Unlike the session controls beside them - see [ReaderTranslationPlacementTest]
 * - these are in the panel whatever the window is doing. They are the two
 * settings somebody wants mid-chapter, and the settings screen is two screens
 * and a closed book away.
 */
@OptIn(ExperimentalTestApi::class)
class ReaderTranslationSettingsTest {

    @Test
    fun theLanguageIsInThePanelOnAPhone() = runComposeUiTest {
        setReader(translationConfigured = true, width = PhoneWidth)

        onNodeWithContentDescription("Reading settings").performClick()

        onNodeWithText("Translate into").assertIsDisplayed()
    }

    /**
     * Even where the toolbar carries the session controls: what the bar has room
     * for is three glyphs, and neither of these is one of them.
     */
    @Test
    fun theLanguageIsInThePanelOnAPointedAtWindowToo() = runComposeUiTest {
        setReader(
            translationConfigured = true,
            translationServiceConfigured = true,
            width = DesktopWidth,
        )

        onNodeWithContentDescription("Reading settings").performClick()

        onNodeWithText("Translate into").assertIsDisplayed()
        onNodeWithText("Press and hold").assertIsDisplayed()
    }

    @Test
    fun nothingIsOfferedUntilThereIsSomethingToTranslateWith() = runComposeUiTest {
        setReader(translationConfigured = false, width = PhoneWidth)

        onNodeWithContentDescription("Reading settings").performClick()

        assertEquals(
            0,
            onAllNodes(hasText("Translate into")).fetchSemanticsNodes().size,
            "a language picker for a translator that does not exist",
        )
    }

    /** The list is a press away, so the panel does not open on seventeen rows. */
    @Test
    fun theLanguagesAreBehindTheOneInForce() = runComposeUiTest {
        var chosen: TargetLanguage? = null

        setReader(
            targetLanguage = TargetLanguage.English,
            onTargetLanguageChange = { chosen = it },
            translationConfigured = true,
            width = PhoneWidth,
        )

        onNodeWithContentDescription("Reading settings").performClick()

        assertEquals(
            0,
            onAllNodes(hasText(TargetLanguage.Finnish.endonym)).fetchSemanticsNodes().size,
            "the whole list was open before anybody asked for it",
        )

        onNodeWithContentDescription("Translate into. English").performClick()
        onNodeWithText(TargetLanguage.Finnish.endonym).performClick()

        assertEquals(TargetLanguage.Finnish, chosen)
    }

    /**
     * Choosing puts the list away again. A panel left showing sixteen languages
     * has pushed the type settings under it off the bottom of a phone.
     */
    @Test
    fun choosingALanguageClosesTheList() = runComposeUiTest {
        setReader(translationConfigured = true, width = PhoneWidth)

        onNodeWithContentDescription("Reading settings").performClick()
        onNodeWithContentDescription("Translate into. English").performClick()
        onNodeWithText(TargetLanguage.Swedish.endonym).performClick()
        waitForIdle()

        assertEquals(
            0,
            onAllNodes(hasText(TargetLanguage.Swedish.endonym)).fetchSemanticsNodes().size,
        )
    }

    /**
     * With a service set up, which is the only reader for whom a tap has units to
     * choose between - a dictionary answers a word and nothing larger.
     */
    @Test
    fun whatATapAsksForCanBeChangedWithTheBookOpen() = runComposeUiTest {
        var gestures: TranslationGestures? = null

        setReader(
            gestures = TranslationGestures(tap = PassageUnit.Sentence, longPress = null),
            onTranslationGesturesChange = { gestures = it },
            translationConfigured = true,
            translationServiceConfigured = true,
            width = PhoneWidth,
        )

        onNodeWithContentDescription("Reading settings").performClick()
        // Both gesture rows offer the same four choices, and the tap is the
        // first of them.
        onAllNodesWithText(words(Res.string.passage_unit_paragraph))[0].performClick()

        assertEquals(
            TranslationGestures(tap = PassageUnit.Paragraph, longPress = null),
            gestures,
            "the tap is the first of the two gesture rows",
        )
    }

    /**
     * And without one it is a switch instead. A reader with a dictionary can
     * have a tap or not have one; offering them the sentence and the paragraph
     * is offering two choices that quietly do nothing.
     */
    @Test
    fun aReaderWithNoServiceIsOfferedTheTapAndNotTheUnits() = runComposeUiTest {
        var gestures: TranslationGestures? = null

        setReader(
            gestures = TranslationGestures(),
            onTranslationGesturesChange = { gestures = it },
            translationConfigured = true,
            width = PhoneWidth,
        )

        onNodeWithContentDescription("Reading settings").performClick()

        assertEquals(
            0,
            onAllNodes(hasText(words(Res.string.passage_unit_paragraph))).fetchSemanticsNodes().size,
            "a paragraph nothing on this device can translate",
        )

        onNodeWithText("Tap a word to translate it").performClick()

        assertEquals(TranslationGestures(tap = PassageUnit.Word), gestures)
    }
}

private val PhoneWidth = 390.dp

/** Past the compact threshold, so the bar is the one with the room. */
private val DesktopWidth = 1000.dp
