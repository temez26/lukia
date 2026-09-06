package io.lukia.features.reader.presentation.screens.translation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import io.lukia.core.domain.AppError
import io.lukia.features.reader.domain.text.TextAnchor
import io.lukia.features.reader.presentation.translation.PassageTranslation
import io.lukia.features.translate.domain.Translation
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The translator's asides, and the reader who would rather have their page back.
 *
 * They were the only place a dictionary's other senses could be read for most of
 * this app's life. Holding a word opens all of them now, at length and out of the
 * way of the prose - so a reader who has found that can turn the ones under the
 * paragraph off.
 *
 * A failure is not one of them, and stays whatever the setting says: a passage
 * marked as translated with nothing to show and no reason given is a reader
 * wondering what happened.
 */
@OptIn(ExperimentalTestApi::class)
class ReaderTranslationNotesTest {

    @Test
    fun aRemarkIsSetUnderTheParagraphWhileTheyAreAskedFor() = runComposeUiTest {
        setReader(translations = { remarked }, translationNotes = true)

        onNodeWithText(remark, substring = true).assertExists()
    }

    @Test
    fun aRemarkIsGoneOnceTheyAreTurnedOff() = runComposeUiTest {
        setReader(translations = { remarked }, translationNotes = false)

        onNodeWithText(remark, substring = true).assertDoesNotExist()
    }

    /** The translation itself is the text, not an aside about it, and it stays. */
    @Test
    fun theTranslationItselfIsNotANote() = runComposeUiTest {
        setReader(translations = { remarked }, translationNotes = false)

        onNodeWithText(translated, substring = true).assertExists()
    }

    @Test
    fun whyAPassageCouldNotBeTranslatedIsShownWhateverTheSettingSays() = runComposeUiTest {
        setReader(
            translations = {
                mapOf(
                    TextAnchor.Section(0, 0, 0) to
                        PassageTranslation.Failed(AppError.Unexpected("The service refused it.")),
                )
            },
            translationNotes = false,
        )

        onNodeWithText("The service refused it.", substring = true).assertExists()
    }

    /** Turning them off is a change to the page, not to what has been paid for. */
    @Test
    fun theSentenceIsStillMarkedAsTranslated() = runComposeUiTest {
        setReader(translations = { remarked }, translationNotes = false)

        assertEquals(
            0,
            onAllNodes(hasText(opening, substring = true)).fetchSemanticsNodes().size,
        )
    }

    private companion object {

        const val remark = "“Ishmael” is kept as the book writes it."

        val remarked: Map<TextAnchor, PassageTranslation> = mapOf(
            TextAnchor.Section(0, 0, 0) to PassageTranslation.Ready(
                Translation(translated, note = remark),
            ),
        )
    }
}
