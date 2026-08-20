@file:OptIn(ExperimentalTestApi::class)

package io.lukia.features.reader.presentation.screens

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performClick
import io.lukia.core.domain.DocumentInfo
import io.lukia.core.domain.DocumentType
import io.lukia.core.domain.PickedDocument
import io.lukia.core.domain.WorkingCopy
import io.lukia.core.presentation.UiState
import io.lukia.features.reader.domain.DocumentPages
import io.lukia.features.reader.domain.OutlineEntry
import io.lukia.features.reader.domain.PageSize
import io.lukia.features.reader.domain.PagedContent
import io.lukia.features.reader.domain.ReaderDocument
import io.lukia.features.reader.domain.ReaderOutline
import io.lukia.features.reader.domain.ReaderSettings
import io.lukia.features.reader.domain.ReadingProgress
import io.lukia.shared.presentation.theme.LukiaTheme

/**
 * One reader set up for a test, and the book it is showing.
 *
 * Shared by the reader screen's test classes rather than repeated in each: a
 * helper per class is a screen per class, and a difference between two of them
 * is a difference nobody meant to write.
 */

internal fun ComposeUiTest.setReader(
    pageCount: Int,
    outline: ReaderOutline = ReaderOutline.Chapters(emptyList()),
    settings: ReaderSettings = ReaderSettings(),
    initialPosition: ReadingProgress? = null,
    onBack: () -> Unit = {},
    onSettingsChange: (ReaderSettings) -> Unit = {},
    onPageSettled: (Int, Int) -> Unit = { _, _ -> },
) {
    setContent {
        LukiaTheme {
            ReaderContent(
                uiState = UiState.Success(readerDocument(pageCount)),
                settings = settings,
                outline = outline,
                initialPosition = initialPosition,
                onBack = onBack,
                onRetry = {},
                onSettingsChange = onSettingsChange,
                onPageSettled = onPageSettled,
                // Pages never arrive: this is about the chrome around them, and a
                // page's own sheet is laid out before it renders.
                renderPage = { _, _ -> null },
            )
        }
    }
}

/**
 * The indicator is the pressable one; the page behind says the same thing.
 *
 * Matched on the page rather than the whole label, because the indicator names
 * the chapter in view ahead of the numbers wherever the book has chapters.
 */
internal fun ComposeUiTest.openContents(pageCount: Int) {
    onNode(
        hasContentDescription("Page 1 of $pageCount", substring = true) and hasClickAction(),
    ).performClick()
}

internal val chapters = ReaderOutline.Chapters(
    listOf(
        OutlineEntry(title = "Introduction", pageIndex = 0, level = 0),
        OutlineEntry(title = "The Middle", pageIndex = 119, level = 0),
        OutlineEntry(title = "A Section", pageIndex = 129, level = 1),
    ),
)

internal fun readerDocument(pageCount: Int) = ReaderDocument(
    info = DocumentInfo(
        document = PickedDocument(
            fileName = "book.pdf",
            extension = "pdf",
            location = "/library/book.pdf",
        ),
        type = DocumentType.PDF,
        exists = true,
        sizeBytes = 1024L,
    ),
    workingCopy = WorkingCopy(
        sourceLocation = "/library/book.pdf",
        path = "/workspace/book.pdf",
    ),
    content = PagedContent(FakePages(pageCount)),
)

private class FakePages(pageCount: Int) : DocumentPages {

    override val outline: List<OutlineEntry> = emptyList()

    // Wide and short, so that more than one page fits the test window and the
    // pages either side of the first are composed. Real pages are taller;
    // nothing here depends on their proportions.
    override val pageSizes: List<PageSize> =
        List(pageCount) { PageSize(width = 800f, height = 300f) }

    override suspend fun renderPage(index: Int, targetWidthPx: Int): ImageBitmap? = null

    override fun close() = Unit
}
