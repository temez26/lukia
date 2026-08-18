package io.lukia.features.reader.presentation.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import io.lukia.core.domain.AppError
import io.lukia.core.domain.BookMetadata
import io.lukia.core.domain.DocumentInfo
import io.lukia.core.domain.DocumentType
import io.lukia.core.domain.PickedDocument
import io.lukia.core.domain.WorkingCopy
import io.lukia.core.presentation.UiState
import io.lukia.features.reader.domain.ReaderDocument
import io.lukia.features.reader.domain.ReaderOutline
import io.lukia.features.reader.domain.ReaderSettings
import io.lukia.features.reader.domain.ReflowableContent
import io.lukia.features.reader.domain.TextBlock
import io.lukia.features.reader.domain.TextRun
import io.lukia.features.reader.domain.TextSection
import io.lukia.features.reader.presentation.TranslateOnScreen
import io.lukia.features.translate.domain.TextAnchor
import io.lukia.features.translate.domain.Translation
import io.lukia.features.translate.presentation.PassageSelection
import io.lukia.features.translate.presentation.PassageTranslation
import io.lukia.shared.presentation.theme.LukiaTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The reflowable reader as somebody actually uses it: point at a sentence, see
 * it change, point at it again to get the original back.
 *
 * Composed rather than unit-tested because what is worth proving here is that a
 * tap on particular words reaches the right sentence of the right block, and
 * that a translation is drawn where the sentence was. Both are questions about
 * layout, which nothing below the screen can answer. How display offsets map
 * back to sentences once text has been replaced is settled deterministically in
 * `ParagraphTextTest` instead - through a layout it would only be settled to
 * within a font metric.
 */
@OptIn(ExperimentalTestApi::class)
class ReaderTranslationTest {

    private val opening = "Call me Ishmael."
    private val second = "Some years ago I went to sea."
    private val translated = "Kutsukaa minua Ismaeliksi."
    private val afterword =
        "No. This paragraph opens with a very short sentence indeed, and then runs on."

    @Test
    fun tappingASentenceAsksForThatSentence() = runComposeUiTest {
        var selected: PassageSelection? = null

        setReader(onPassageSelected = { selected = it })
        onNodeWithText(opening, substring = true).clickFirstWords()

        val selection = assertNotNull(selected, "no sentence was selected")
        assertEquals(opening, selection.passage)
        assertEquals(TextAnchor.Section(0, 0, 0), selection.anchor)
    }

    /**
     * The paragraph travels with the sentence: it is what tells a translator
     * which sense of an ambiguous word the book meant.
     */
    @Test
    fun theSentenceArrivesWithTheParagraphAroundIt() = runComposeUiTest {
        var selected: PassageSelection? = null

        setReader(onPassageSelected = { selected = it })
        onNodeWithText(opening, substring = true).clickFirstWords()

        val selection = assertNotNull(selected)
        assertEquals("$opening $second", selection.surroundingText)
        assertEquals("Loomings", selection.chapterTitle)
    }

    /** A sentence in the paragraph below is a different block, not a later sentence. */
    @Test
    fun eachParagraphCountsItsOwnSentences() = runComposeUiTest {
        var selected: PassageSelection? = null

        setReader(onPassageSelected = { selected = it })
        onNodeWithText("It was cold.", substring = true).clickFirstWords()

        assertEquals(TextAnchor.Section(0, 1, 0), assertNotNull(selected).anchor)
    }

    /**
     * A later sentence of the same paragraph is its own passage. This
     * paragraph's opening sentence is deliberately tiny, so the middle of it is
     * unambiguously inside the sentence that follows.
     */
    @Test
    fun aLaterSentenceOfTheSameParagraphIsItsOwnPassage() = runComposeUiTest {
        var selected: PassageSelection? = null

        setReader(onPassageSelected = { selected = it })
        onNodeWithText(afterword, substring = true).performTouchInput { click() }

        assertEquals(TextAnchor.Section(0, 2, 1), assertNotNull(selected).anchor)
    }

    @Test
    fun aTranslatedSentenceIsShownInPlaceOfTheOriginal() = runComposeUiTest {
        setReader(
            translations = {
                mapOf(
                    TextAnchor.Section(0, 0, 0) to PassageTranslation.Ready(Translation(translated))
                )
            }
        )

        onNodeWithText(translated, substring = true).assertExists()
        // The rest of the paragraph is untouched, and the two are still one block.
        onNodeWithText(second, substring = true).assertExists()
    }

    /** Asking for the original back puts the book's own words on screen again. */
    @Test
    fun flippingBackShowsTheBooksOwnWords() = runComposeUiTest {
        setReader(
            translations = {
                mapOf(
                    TextAnchor.Section(0, 0, 0) to PassageTranslation.Ready(
                        translation = Translation(translated),
                        showingOriginal = true
                    )
                )
            }
        )

        onNodeWithText(opening, substring = true).assertExists()
        assertEquals(
            0,
            onAllNodes(hasText(translated, substring = true)).fetchSemanticsNodes().size
        )
    }

    /**
     * A remark about an ambiguous word is worth showing, under the paragraph it
     * is about rather than set into it.
     */
    @Test
    fun aTranslatorsNoteIsShownUnderTheParagraph() = runComposeUiTest {
        setReader(
            translations = {
                mapOf(
                    TextAnchor.Section(0, 0, 0) to PassageTranslation.Ready(
                        Translation(translated, note = "\"Ishmael\" is kept as the book writes it.")
                    )
                )
            }
        )

        onNodeWithText("is kept as the book writes it", substring = true).assertExists()
    }

    /** Somebody reading the original is reading the original, notes and all. */
    @Test
    fun theNoteGoesAwayWithTheTranslation() = runComposeUiTest {
        setReader(
            translations = {
                mapOf(
                    TextAnchor.Section(0, 0, 0) to PassageTranslation.Ready(
                        translation = Translation(translated, note = "A remark about a word."),
                        showingOriginal = true
                    )
                )
            }
        )

        assertEquals(
            0,
            onAllNodes(hasText("A remark about a word.", substring = true)).fetchSemanticsNodes().size
        )
    }

    /** A failure has to say what went wrong, not just tint the words. */
    @Test
    fun aFailedTranslationSaysWhyUnderTheParagraph() = runComposeUiTest {
        setReader(
            translations = {
                mapOf(
                    TextAnchor.Section(0, 0, 0) to PassageTranslation.Failed(
                        AppError.Unexpected("That passage is too long to translate.")
                    )
                )
            }
        )

        onNodeWithText("too long to translate", substring = true).assertExists()
        // And the book's own words are still there to read.
        onNodeWithText(opening, substring = true).assertExists()
    }

    /** While it is being translated the reader still has something to read. */
    @Test
    fun aSentenceStillBeingTranslatedKeepsItsWords() = runComposeUiTest {
        setReader(
            translations = { mapOf(TextAnchor.Section(0, 0, 0) to PassageTranslation.Loading()) }
        )

        onNodeWithText(opening, substring = true).assertExists()
    }

    /**
     * The whole of what a reader sees after tapping, in order: their own words,
     * the same words while the answer is on its way, and then the translation in
     * place of them - with the rest of the paragraph untouched throughout.
     *
     * A paragraph is only rebuilt when the words in it actually change, so this
     * is also the test that a sentence going from untouched to waiting still
     * leaves something readable on screen.
     */
    @Test
    fun aSentenceGoesFromTheBooksWordsToTheTranslationInPlace() = runComposeUiTest {
        var translations by mutableStateOf<Map<TextAnchor, PassageTranslation>>(emptyMap())

        setReader(translations = { translations })

        onNodeWithText(opening, substring = true).assertExists()

        translations = mapOf(TextAnchor.Section(0, 0, 0) to PassageTranslation.Loading())
        waitForIdle()

        // Waiting is not a reason to take the reader's book away from them.
        onNodeWithText(opening, substring = true).assertExists()

        translations = mapOf(
            TextAnchor.Section(0, 0, 0) to PassageTranslation.Ready(Translation(translated))
        )
        waitForIdle()

        onNodeWithText(translated, substring = true).assertExists()
        onNodeWithText(second, substring = true).assertExists()
    }


    /**
     * Holding a word asks about that word alone. A tap on the same words still
     * means the sentence, which is what lets one gesture cover both without a
     * mode to switch between them.
     */
    @Test
    fun holdingAWordAsksAboutThatWord() = runComposeUiTest {
        var selected: PassageSelection? = null

        setReader(onPassageSelected = { selected = it })
        onNodeWithText(opening, substring = true).performTouchInput {
            longClick(Offset(4f, 4f))
        }

        val selection = assertNotNull(selected, "no word was selected")
        assertEquals("Call", selection.passage)
        assertEquals(TextAnchor.Section(0, 0, 0, wordIndex = 0), selection.anchor)
    }

    /** A word travels with the paragraph around it, exactly as a sentence does. */
    @Test
    fun aWordArrivesWithTheParagraphAroundIt() = runComposeUiTest {
        var selected: PassageSelection? = null

        setReader(onPassageSelected = { selected = it })
        onNodeWithText(opening, substring = true).performTouchInput {
            longClick(Offset(4f, 4f))
        }

        assertEquals("$opening $second", assertNotNull(selected).surroundingText)
    }

    /** One translated word stands in its sentence, and the sentence stays the book's. */
    @Test
    fun aTranslatedWordIsShownInsideTheBooksOwnSentence() = runComposeUiTest {
        setReader(
            translations = {
                mapOf(
                    TextAnchor.Section(0, 0, 0, wordIndex = 2) to
                        PassageTranslation.Ready(Translation("Ismaeliksi"))
                )
            }
        )

        onNodeWithText("Call me Ismaeliksi.", substring = true).assertExists()
    }

    /**
     * Asking for what is on screen asks about every sentence on it, and asks
     * about each of them once.
     */
    @Test
    fun askingForWhatIsOnScreenAsksAboutTheSentencesThatAreOnIt() = runComposeUiTest {
        val requested = mutableListOf<PassageSelection>()
        var translate by mutableStateOf(TranslateOnScreen.Idle)

        setReader(
            onPassagesRequested = { requested += it },
            translateOnScreen = { translate }
        )

        // Nothing until it is asked for: this is not a standing order.
        assertEquals(0, requested.size)

        translate = translate.requested()
        waitForIdle()

        val passages = requested.map { it.passage }

        assertTrue(passages.contains(opening), "$passages")
        assertTrue(passages.contains(second), "$passages")
        assertEquals(passages.size, passages.distinct().size, "a sentence was asked about twice")
    }

    /**
     * A tap lands where it is aimed. The node is the whole paragraph, so its
     * centre is whichever sentence happens to fall in the middle of it - the
     * first few pixels are always the first sentence.
     */
    private fun SemanticsNodeInteraction.clickFirstWords() =
        performTouchInput { click(Offset(4f, 4f)) }

    /**
     * The translations are read inside the composition rather than captured, so
     * a test can change them and watch the paragraph follow - which is what
     * actually happens when an answer comes back.
     */
    private fun ComposeUiTest.setReader(
        translations: () -> Map<TextAnchor, PassageTranslation> = { emptyMap() },
        onPassageSelected: (PassageSelection) -> Unit = {},
        onPassagesRequested: (List<PassageSelection>) -> Unit = {},
        translateOnScreen: () -> TranslateOnScreen = { TranslateOnScreen.Idle }
    ) {
        setContent {
            LukiaTheme {
                ReaderContent(
                    uiState = UiState.Success(book()),
                    settings = ReaderSettings(),
                    outline = ReaderOutline.Chapters(emptyList()),
                    translations = translations(),
                    translateOnScreen = translateOnScreen(),
                    onBack = {},
                    onRetry = {},
                    onSettingsChange = {},
                    onPassageSelected = onPassageSelected,
                    onPassagesRequested = onPassagesRequested,
                    renderPage = { _, _ -> null }
                )
            }
        }
    }

    private fun book() = ReaderDocument(
        info = DocumentInfo(
            document = PickedDocument(
                fileName = "moby-dick.epub",
                extension = "epub",
                location = "/library/moby-dick.epub"
            ),
            type = DocumentType.EPUB,
            exists = true,
            sizeBytes = 1024L,
            metadata = BookMetadata(
                title = "Moby-Dick",
                authors = listOf("Herman Melville"),
                subjects = listOf("Adventure fiction")
            )
        ),
        workingCopy = WorkingCopy(
            sourceLocation = "/library/moby-dick.epub",
            path = "/workspace/moby-dick.epub"
        ),
        content = ReflowableContent(
            sections = listOf(
                TextSection(
                    title = "Loomings",
                    blocks = listOf(
                        TextBlock.Paragraph(listOf(TextRun("$opening $second"))),
                        TextBlock.Paragraph(listOf(TextRun("It was cold."))),
                        TextBlock.Paragraph(listOf(TextRun(afterword)))
                    )
                )
            ),
            outline = emptyList()
        )
    )
}
