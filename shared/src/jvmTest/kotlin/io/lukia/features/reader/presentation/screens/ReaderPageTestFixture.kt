@file:OptIn(ExperimentalTestApi::class)

package io.lukia.features.reader.presentation.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import io.lukia.core.domain.BookMetadata
import io.lukia.core.domain.DocumentInfo
import io.lukia.core.domain.DocumentType
import io.lukia.core.domain.PickedDocument
import io.lukia.core.domain.WorkingCopy
import io.lukia.core.presentation.UiState
import io.lukia.features.reader.domain.DocumentPages
import io.lukia.features.reader.domain.OutlineEntry
import io.lukia.features.reader.domain.PageSentence
import io.lukia.features.reader.domain.PageSize
import io.lukia.features.reader.domain.PageWord
import io.lukia.features.reader.domain.PagedContent
import io.lukia.features.reader.domain.ReaderDocument
import io.lukia.features.reader.domain.ReaderOutline
import io.lukia.features.reader.domain.ReaderSettings
import io.lukia.features.reader.domain.TextQuad
import io.lukia.features.translate.domain.TextAnchor
import io.lukia.features.translate.domain.TranslationGestures
import io.lukia.features.translate.presentation.PassageSelection
import io.lukia.features.translate.presentation.PassageTranslation
import io.lukia.shared.presentation.theme.LukiaTheme

/**
 * A two-page PDF and the sentences printed on it.
 *
 * The sample pages are shaped for what they have to prove: one sentence
 * covering a whole page, a page of separate words, the same page with a gap
 * between two of them, and a word in the corner.
 */

/** Covers the whole page, so a tap anywhere on it is a tap on these words. */
internal val wholePage = PageSentence(
    index = 0,
    text = opening,
    quads = listOf(TextQuad(left = 0f, top = 0f, right = 800f, bottom = 300f)),
)

/**
 * The same sentence with its words placed, as a real page gives them.
 *
 * The second word covers the middle of the page, which is where a tap on the
 * page node lands - so a tap that reaches the words at all reaches that one,
 * and a tap that only reaches the sentence is visibly a different answer.
 */
internal val pageOfWords = PageSentence(
    index = 0,
    text = opening,
    quads = listOf(TextQuad(left = 0f, top = 0f, right = 800f, bottom = 300f)),
    words = listOf(
        PageWord(
            index = 0,
            text = "Call",
            quads = listOf(TextQuad(left = 0f, top = 0f, right = 200f, bottom = 300f)),
        ),
        PageWord(
            index = 1,
            text = "Ishmael",
            quads = listOf(TextQuad(left = 220f, top = 0f, right = 800f, bottom = 300f)),
        ),
    ),
)

/** The same page with nothing under the middle of it, which is where a tap lands. */
internal val pageWithAGapInTheMiddle = pageOfWords.copy(
    words = pageOfWords.words.map { word ->
        word.copy(
            quads = listOf(TextQuad(left = 0f, top = 0f, right = 20f, bottom = 20f)),
        )
    },
)

/** A second block of the same page, far enough down to be one. */
internal val secondBlock = PageSentence(
    index = 1,
    text = "Some years ago.",
    quads = listOf(TextQuad(left = 0f, top = 280f, right = 100f, bottom = 300f)),
    blockIndex = 1,
)

/**
 * A page whose word is far smaller than the translation drawn over it, which is
 * what every word on a real page is: the panel starts at the word's corner and
 * runs as wide and as far down as another language needs, so almost none of
 * what the reader can see of it is over the word itself.
 */
internal val pageWithATinyWord = PageSentence(
    index = 0,
    text = opening,
    quads = listOf(TextQuad(left = 0f, top = 0f, right = 800f, bottom = 20f)),
    words = listOf(
        PageWord(
            index = 0,
            text = "Call",
            quads = listOf(TextQuad(left = 0f, top = 0f, right = 12f, bottom = 8f)),
        ),
    ),
)

/** A word in the very corner, so the middle of the page is not on it. */
internal val cornerWord = PageSentence(
    index = 0,
    text = opening,
    quads = listOf(TextQuad(left = 0f, top = 0f, right = 8f, bottom = 6f)),
)

/**
 * Page two rather than page one throughout: the first page opens underneath
 * the floating chrome, so its middle is not somewhere a tap can be put.
 */

internal fun ComposeUiTest.tapPageTwo() =
    onNodeWithContentDescription("Page 2 of 2").performTouchInput { click() }

/** Two taps in the same place, which is what the chrome answers to. */
internal fun ComposeUiTest.doubleTapPageTwo() =
    onNodeWithContentDescription("Page 2 of 2").performTouchInput { doubleClick() }

/**
 * The translations are read inside the composition rather than captured, so
 * a test can change them and watch the page follow - which is what actually
 * happens when an answer comes back.
 */
internal fun ComposeUiTest.setPageReader(
    sentences: List<PageSentence>,
    translations: () -> Map<TextAnchor, PassageTranslation> = { emptyMap() },
    onPassageSelected: (PassageSelection) -> Unit = {},
    gestures: TranslationGestures = TranslationGestures.Default,
) {
    setContent {
        LukiaTheme {
            ReaderContent(
                uiState = UiState.Success(pageBook()),
                settings = ReaderSettings(),
                outline = ReaderOutline.Chapters(emptyList()),
                translations = translations(),
                onBack = {},
                onRetry = {},
                onSettingsChange = {},
                onPassageSelected = onPassageSelected,
                gestures = gestures,
                sentencesOnPage = { sentences },
                renderPage = { _, _ -> null },
            )
        }
    }
}

private fun pageBook() = ReaderDocument(
    info = DocumentInfo(
        document = PickedDocument(
            fileName = "moby-dick.pdf",
            extension = "pdf",
            location = "/library/moby-dick.pdf",
        ),
        type = DocumentType.PDF,
        exists = true,
        sizeBytes = 1024L,
        metadata = BookMetadata(title = "Moby-Dick"),
    ),
    workingCopy = WorkingCopy(
        sourceLocation = "/library/moby-dick.pdf",
        path = "/workspace/moby-dick.pdf",
    ),
    content = PagedContent(TwoWidePages),
)

/**
 * Wide and short, so both pages fit the test window and each is composed.
 * Real pages are taller; nothing here depends on their proportions.
 */
private object TwoWidePages : DocumentPages {

    override val outline: List<OutlineEntry> = emptyList()

    override val pageSizes: List<PageSize> =
        List(2) { PageSize(width = 800f, height = 300f) }

    override suspend fun renderPage(index: Int, targetWidthPx: Int): ImageBitmap? = null

    override fun close() = Unit
}
