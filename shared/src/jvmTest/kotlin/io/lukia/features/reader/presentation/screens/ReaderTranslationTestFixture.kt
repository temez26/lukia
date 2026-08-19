@file:OptIn(ExperimentalTestApi::class)

package io.lukia.features.reader.presentation.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
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
    translationConfigured: Boolean = false,
    bulkTranslation: BulkTranslation = BulkTranslation.Default,
) {
    setContent {
        LukiaTheme {
            ReaderContent(
                uiState = UiState.Success(book()),
                settings = ReaderSettings(),
                outline = ReaderOutline.Chapters(emptyList()),
                translations = translations(),
                translateOnScreen = translateOnScreen(),
                gestures = gestures,
                translationConfigured = translationConfigured,
                bulkTranslation = bulkTranslation,
                onBack = {},
                onRetry = {},
                onSettingsChange = {},
                onPassageSelected = onPassageSelected,
                onPassagesRequested = onPassagesRequested,
                renderPage = { _, _ -> null },
            )
        }
    }
}

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
