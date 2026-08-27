@file:OptIn(ExperimentalTestApi::class)

package io.lukia.features.reader.presentation.screens

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
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import io.lukia.features.translate.domain.BulkTranslation
import io.lukia.features.translate.domain.TargetLanguage
import io.lukia.features.translate.domain.TextAnchor
import io.lukia.features.translate.domain.TranslationGestures
import io.lukia.features.translate.presentation.PassageSelection
import io.lukia.features.translate.presentation.PassageTranslation
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

/**
 * The translations are read inside the composition rather than captured, so
 * a test can change them and watch the paragraph follow - which is what
 * actually happens when an answer comes back.
 */
internal fun ComposeUiTest.setReader(
    translations: () -> Map<TextAnchor, PassageTranslation> = { emptyMap() },
    onPassageSelected: (PassageSelection) -> Unit = {},
    onPassagesRequested: (List<PassageSelection>) -> Unit = {},
    translateOnScreen: () -> TranslateOnScreen = { TranslateOnScreen.Idle },
    gestures: TranslationGestures = TranslationGestures.Default,
    onTranslationGesturesChange: (TranslationGestures) -> Unit = {},
    targetLanguage: TargetLanguage = TargetLanguage.Default,
    onTargetLanguageChange: (TargetLanguage) -> Unit = {},
    translationConfigured: Boolean = false,
    bulkTranslation: BulkTranslation = BulkTranslation.Default,
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
                translations = translations(),
                translateOnScreen = translateOnScreen(),
                gestures = gestures,
                targetLanguage = targetLanguage,
                translationConfigured = translationConfigured,
                bulkTranslation = bulkTranslation,
                onBack = {},
                onRetry = {},
                onSettingsChange = {},
                onPassageSelected = onPassageSelected,
                onPassagesRequested = onPassagesRequested,
                onTargetLanguageChange = onTargetLanguageChange,
                onTranslationGesturesChange = onTranslationGesturesChange,
                renderPage = { _, _ -> null },
            )
        }
    }

    // The book is laid out into pages before any of it is drawn, and that pass
    // runs off the main thread - so there is a moment after the reader appears
    // when it has no pages in it yet. Waited for by the page rather than by what
    // is written on it, because half these tests open on a book whose words have
    // already been replaced by their translations.
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
