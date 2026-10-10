@file:OptIn(ExperimentalTestApi::class)

package io.lukia.features.reader.presentation.screens.translation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.core.domain.document.BookMetadata
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.domain.document.DocumentType
import io.lukia.core.domain.document.PickedDocument
import io.lukia.core.domain.storage.WorkingCopy
import io.lukia.core.domain.text.PassageUnit
import io.lukia.core.presentation.UiState
import io.lukia.features.reader.domain.ReaderDocument
import io.lukia.features.reader.domain.ReaderSettings
import io.lukia.features.reader.domain.ReflowableContent
import io.lukia.features.reader.domain.outline.ReaderOutline
import io.lukia.features.reader.domain.text.TextAnchor
import io.lukia.features.reader.domain.text.TextBlock
import io.lukia.features.reader.domain.text.TextRun
import io.lukia.features.reader.domain.text.TextSection
import io.lukia.features.reader.presentation.screens.ReaderContent
import io.lukia.features.reader.presentation.translation.PassageSelection
import io.lukia.features.reader.presentation.translation.PassageTranslation
import io.lukia.features.reader.presentation.translation.TranslateOnScreen
import io.lukia.features.reader.presentation.translation.WordInquiry
import io.lukia.features.translate.domain.BulkTranslation
import io.lukia.features.translate.domain.settings.TargetLanguage
import io.lukia.features.translate.domain.settings.TranslationGestures
import io.lukia.shared.presentation.theme.LukiaTheme

/**
 * The book these tests read, and the reader showing it.
 *
 * Shared so that every question about translating reaches the same three
 * paragraphs. The sample sentences are chosen for their shape: one short, one
 * long, and one paragraph that opens with a sentence of two words.
 */

internal val opening = "Call me Ishmael."
internal val second = "Some years ago I went to sea."
internal val translated = "Kutsukaa minua Ismaeliksi."
internal val afterword =
    "No. This paragraph opens with a very short sentence indeed, and then runs on."

internal fun SemanticsNodeInteraction.clickFirstWords() =
    performTouchInput { click(Offset(4f, 4f)) }

/** Two taps in the same place, which is what the chrome answers to. */
internal fun SemanticsNodeInteraction.doubleClickFirstWords() =
    performTouchInput { doubleClick(Offset(4f, 4f)) }

/** The second mouse button, which is two fingers on a laptop's trackpad. */
internal fun SemanticsNodeInteraction.secondButtonOnFirstWords() =
    performMouseInput { rightClick(Offset(4f, 4f)) }

/**
 * A reader who has turned translating on: a tap for the sentence, a hold for the
 * word, and a word able to say more when held again.
 *
 * Named here rather than taken from `TranslationGestures.Default`, which is now
 * every one of them off - a book that does not translate until its reader says
 * so. These tests are about what a gesture does once it has been set, so they
 * set it.
 */
internal val TranslatingReader = TranslationGestures.of(
    tap = PassageUnit.Sentence,
    longPress = PassageUnit.Word,
    wordDetails = true,
)

internal fun ComposeUiTest.setReader(
    /**
     * Read inside the composition rather than captured, so a test can change
     * them and watch the paragraph follow - which is what happens when an answer
     * comes back.
     */
    translations: () -> Map<TextAnchor, PassageTranslation> = { emptyMap() },
    onPassageSelected: (PassageSelection) -> Unit = {},
    onPassageHold: (PassageSelection) -> Unit = {},
    onPassageInquiry: (PassageSelection) -> Unit = {},
    onPassagesRequested: (List<PassageSelection>) -> Unit = {},
    translateOnScreen: () -> TranslateOnScreen = { TranslateOnScreen.Idle },
    gestures: TranslationGestures = TranslatingReader,
    onTranslationGesturesChange: (TranslationGestures) -> Unit = {},
    targetLanguage: TargetLanguage = TargetLanguage.Default,
    onTargetLanguageChange: (TargetLanguage) -> Unit = {},
    /**
     * Whether the translation service is set up.
     *
     * On by default, to match [TranslatingReader]: these gestures ask for a
     * sentence and a word, and without a service a gesture set past the word is
     * taken back to the word before the page ever sees it - see
     * `ReaderBookContent`. A test about the reader who has no service says so.
     */
    translationServiceConfigured: Boolean = true,
    bulkTranslation: BulkTranslation = BulkTranslation.Default,
    /**
     * Read inside the composition rather than captured, like the translations
     * above - so a test can take the word away and watch the panel leave.
     */
    wordInquiry: () -> WordInquiry? = { null },
    translationNotes: Boolean = true,
    /**
     * How wide the window is, or null for whatever the test host gives - which
     * is a desktop-sized one. Only the tests that care where a control lives
     * name it, since the theme reads the size to decide that.
     */
    width: Dp? = null,
) {
    setContent {
        WindowOf(width) {
            ReaderContent(
                uiState = UiState.Success(book()),
                settings = ReaderSettings(),
                outline = ReaderOutline.Chapters(emptyList()),
                translating = ReaderTranslating(
                    passages = translations(),
                    onScreen = translateOnScreen(),
                    gestures = gestures,
                    targetLanguage = targetLanguage,
                    serviceConfigured = translationServiceConfigured,
                    offered = bulkTranslation,
                    wordInquiry = wordInquiry(),
                    notes = translationNotes,
                ),
                translatingActions = ReaderTranslatingActions(
                    onPassageSelected = onPassageSelected,
                    onPassageHold = onPassageHold,
                    onPassageInquiry = onPassageInquiry,
                    onPassagesRequested = onPassagesRequested,
                    onTargetLanguageChange = onTargetLanguageChange,
                    onGesturesChange = onTranslationGesturesChange,
                ),
                onBack = {},
                onRetry = {},
                onSettingsChange = {},
                renderPage = { _, _ -> null },
            )
        }
    }

    // The book is laid out into pages before any of it is drawn, and that pass
    // runs off the main thread - so there is a moment after the reader appears
    // when it has no pages in it yet. Waited for by the page rather than by what
    // is written on it, because half these tests open on a book whose words have
    // already been replaced by their translations.
    //
    // Except where the reader opens with the word panel already up: the book
    // behind an open panel is deliberately out of the accessibility tree - see
    // `behindOverlay` - so there is no page node, and waiting for one would be
    // waiting for the modality to fail. Nothing needs waiting for there anyway,
    // since the panel is composed in the same frame as the reader.
    if (wordInquiry() != null) return

    waitUntil {
        onAllNodes(hasContentDescription("Page 1 of ", substring = true))
            .fetchSemanticsNodes()
            .isNotEmpty()
    }
}

/** A window of a chosen width, themed, so a test can ask for a phone. */
@Composable
private fun WindowOf(width: Dp?, content: @Composable () -> Unit) {
    if (width == null) {
        LukiaTheme(content = content)
    } else {
        Box(modifier = Modifier.size(width, PhoneHeight)) {
            LukiaTheme(content = content)
        }
    }
}

/** Tall enough that a panel opened in a sized window is not cut off. */
private val PhoneHeight = 760.dp

internal fun book() = ReaderDocument(
    info = DocumentInfo(
        document = PickedDocument(
            fileName = "moby-dick.epub",
            extension = "epub",
            location = "/library/moby-dick.epub",
        ),
        type = DocumentType.EPUB,
        exists = true,
        sizeBytes = 1024L,
        metadata = BookMetadata(
            title = "Moby-Dick",
            authors = listOf("Herman Melville"),
            subjects = listOf("Adventure fiction"),
        ),
    ),
    workingCopy = WorkingCopy(
        sourceLocation = "/library/moby-dick.epub",
        path = "/workspace/moby-dick.epub",
    ),
    content = ReflowableContent(
        sections = listOf(
            TextSection(
                title = "Loomings",
                blocks = listOf(
                    TextBlock.Paragraph(listOf(TextRun("$opening $second"))),
                    TextBlock.Paragraph(listOf(TextRun("It was cold."))),
                    TextBlock.Paragraph(listOf(TextRun(afterword))),
                ),
            ),
        ),
        outline = emptyList(),
    ),
)
