package io.lukia.features.reader.presentation.components.contents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.presentation.authorLine
import io.lukia.core.presentation.bookTitle
import io.lukia.features.reader.domain.outline.ReaderOutline
import io.lukia.features.reader.presentation.currentEntryIndex
import io.lukia.features.reader.presentation.pagesLeftInEntry
import io.lukia.features.reader.presentation.percentThrough
import io.lukia.features.reader.presentation.theme.ReaderPanelWidth
import io.lukia.resources.Res
import io.lukia.resources.reader_go_to_page
import io.lukia.resources.reader_page_x_of_y
import io.lukia.resources.reader_page_x_of_y_percent
import io.lukia.resources.reader_pages_left_in_chapter
import io.lukia.resources.reader_percent
import io.lukia.shared.presentation.components.cover.BookCover
import io.lukia.shared.presentation.components.cover.rememberBookCover
import io.lukia.shared.presentation.components.menu.DismissOnBack
import io.lukia.shared.presentation.components.menu.popoverTransition
import io.lukia.shared.presentation.components.menu.rememberPopoverProgress
import io.lukia.shared.presentation.components.surface.GlassSurface
import io.lukia.shared.presentation.components.surface.GlassTone
import io.lukia.shared.presentation.components.surface.LukiaDivider
import io.lukia.shared.presentation.components.surface.TopSheet
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.Space
import io.lukia.shared.presentation.theme.isWideWindow
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** Grows out of the control it hangs from, matching the other reader panels. */
private val PanelTransformOrigin = TransformOrigin(0.5f, 0f)

/** Between the page and what is left of the chapter, as the people list sets its facts. */
private const val Separator = " · "

/** A cover small enough to sit beside two lines of title, in a book's proportions. */
private val HeaderCoverWidth: Dp = 40.dp
private val HeaderCoverHeight: Dp = 60.dp
private val HeaderMonogramSize = 18.sp

/** Tall enough to drag, short enough not to be the loudest thing in the panel. */
private val ScrubberHeight: Dp = 28.dp

/** What the contents panel can do to the reader around it. */
internal class ContentsActions(
    val onGoToPage: (pageIndex: Int) -> Unit,
    /** An entry of the outline by its index, which may not have a page yet. */
    val onGoToChapter: (entryIndex: Int) -> Unit,
    val onDismiss: () -> Unit,
)

/**
 * Getting somewhere else in the book: the book itself, a scrubber for the whole
 * of it, and its own table of contents underneath.
 *
 * On a phone, a sheet that opens down out of the reader's bar, the bar its top
 * row: one block from the page indicator that opened it to the last chapter, and
 * the whole height of the screen for the list. A wider window has the room to
 * keep it a popover hanging from the page indicator, beside the page it changes.
 */
@Composable
internal fun ReaderContentsPanel(
    expanded: Boolean,
    listState: LazyListState,
    pageCount: Int,
    /** False while a reflowable book is still being measured and [pageCount] climbs. */
    pageCountFinal: Boolean,
    outline: ReaderOutline,
    book: DocumentInfo,
    actions: ContentsActions,
    modifier: Modifier = Modifier,
    /** How tall the reader's bar is, which a phone's sheet leaves room for at its top. */
    barHeight: Dp = 0.dp,
) {
    val contents: @Composable ColumnScope.() -> Unit = {
        ContentsBody(listState, pageCount, pageCountFinal, outline, book, actions)
    }

    if (isWideWindow) {
        ContentsPopover(expanded, actions.onDismiss, modifier = modifier, content = contents)
    } else {
        TopSheet(
            visible = expanded,
            onDismiss = actions.onDismiss,
            topClearance = barHeight,
            modifier = modifier,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = Space.md).weight(1f, fill = false),
                content = contents,
            )
        }
    }
}

@Composable
private fun ContentsPopover(
    expanded: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val progress = rememberPopoverProgress(expanded, label = "readerContentsPanel")

    if (!expanded && progress == 0f) return

    DismissOnBack(enabled = expanded, onDismiss = onDismiss)

    GlassSurface(
        shape = MaterialTheme.shapes.large,
        tone = GlassTone.Panel,
        modifier = modifier.popoverTransition(progress, PanelTransformOrigin),
    ) {
        Column(
            modifier = Modifier
                .width(ReaderPanelWidth)
                .padding(vertical = Space.md),
            content = content,
        )
    }
}

@Composable
private fun ColumnScope.ContentsBody(
    listState: LazyListState,
    pageCount: Int,
    pageCountFinal: Boolean,
    outline: ReaderOutline,
    book: DocumentInfo,
    actions: ContentsActions,
) {
    // Read here rather than passed in: the page changes on every scroll, and
    // taking it any higher would recompose the reader around the panel each
    // time. Inside the panel it costs nothing while the panel is closed.
    val currentPage by remember(listState) {
        derivedStateOf { listState.firstVisibleItemIndex + 1 }
    }

    val inset = LocalLukiaDimens.current.panelPadding
    val entries = (outline as? ReaderOutline.Chapters)?.entries.orEmpty()

    BookHeader(book = book, inset = inset)

    PositionScrubber(
        current = currentPage,
        pageCount = pageCount,
        chapterAt = { page -> entries.getOrNull(currentEntryIndex(entries, page - 1))?.title },
        pagesLeft = { page -> pagesLeftInEntry(entries, page - 1, pageCount, pageCountFinal) },
        inset = inset,
        onGoTo = actions.onGoToPage,
    )

    LukiaDivider(
        inset = inset,
        endInset = inset,
        modifier = Modifier.padding(vertical = Space.md),
    )

    Chapters(
        outline = outline,
        currentPage = currentPage,
        pageCount = pageCount,
        pageCountFinal = pageCountFinal,
        inset = inset,
        // As tall as the window allows and no taller than the contents: a
        // contents list is read by scanning, and a fixed box of seven rows made
        // a book of forty chapters a list to scroll through to find any of them.
        modifier = Modifier.weight(1f, fill = false),
        onOpen = {
            actions.onGoToChapter(it)
            actions.onDismiss()
        },
    )
}

/** Which book this is, as every reader's contents opens: cover, title and author. */
@Composable
private fun BookHeader(book: DocumentInfo, inset: Dp) {
    val title = book.bookTitle()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.lg),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = inset, end = inset, bottom = Space.lg)
            .semantics(mergeDescendants = true) {},
    ) {
        BookCover(
            cover = rememberBookCover(book.document.location, book.coverImage?.bytes),
            title = title,
            shape = MaterialTheme.shapes.small,
            elevation = 0.dp,
            monogramFontSize = HeaderMonogramSize,
            modifier = Modifier.size(width = HeaderCoverWidth, height = HeaderCoverHeight),
        )

        Column(verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            book.metadata?.authorLine()?.let { authors ->
                Text(
                    text = authors,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Drag anywhere in the book.
 *
 * The page only changes when the drag ends. Following the scrubber live would
 * mean rendering every page it passed over, which is exactly the work that makes
 * a scrubber feel heavy in a long document - so the labels track the thumb and
 * the book waits until it is let go. The chapter name above it is what makes
 * that wait useful: a page number says where the thumb is, the chapter says
 * whether that is where the reader meant to go.
 */
@Composable
private fun PositionScrubber(
    current: Int,
    pageCount: Int,
    chapterAt: (page: Int) -> String?,
    pagesLeft: (page: Int) -> Int?,
    inset: Dp,
    onGoTo: (index: Int) -> Unit,
) {
    var dragged by remember { mutableStateOf<Int?>(null) }

    // Lets go of the drag position once the book has caught up, so the scrubber
    // follows the reader again when they scroll by hand.
    LaunchedEffect(current) { dragged = null }

    val shown = dragged ?: current

    val percent = percentThrough(shown, pageCount)
    val chapter = chapterAt(shown)

    // Where the reader is rather than where the thumb is: how much of this
    // chapter is left says nothing about one they have not opened yet.
    val left = if (dragged == null) pagesLeft(shown) else null
    val leftText = left?.let { pluralStringResource(Res.plurals.reader_pages_left_in_chapter, it, it) }

    // Read before the modifier chain: `semantics` is not a composable scope.
    val position = stringResource(
        Res.string.reader_page_x_of_y_percent,
        shown,
        pageCount,
        percent,
    )
    val spoken = listOfNotNull(chapter, position, leftText).joinToString(". ")

    val goToPage = stringResource(Res.string.reader_go_to_page)

    // The page without the percentage beside it. The row above says how far in;
    // the scrubber's own label says where it would land, and a slider that reads
    // out a percentage every time it moves is announcing two numbers where the
    // reader is only moving one of them.
    val place = stringResource(Res.string.reader_page_x_of_y, shown, pageCount)

    Column(modifier = Modifier.padding(horizontal = inset)) {
        ScrubberPlace(
            chapter = chapter,
            place = listOfNotNull(place, leftText).joinToString(Separator),
            percent = stringResource(Res.string.reader_percent, percent),
            spoken = spoken,
        )

        if (pageCount > 1) {
            Slider(
                value = shown.toFloat(),
                valueRange = 1f..pageCount.toFloat(),
                onValueChange = { dragged = it.roundToInt() },
                onValueChangeFinished = { dragged?.let { position -> onGoTo(position - 1) } },
                // Material's own track is a thick expressive bar in its default
                // colours, which is the one control in the app that looked like it
                // came from a different application. Dressed in the panel's own
                // palette and given the height of a scrubber rather than a slider.
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
                ),
                modifier = Modifier
                    .height(ScrubberHeight)
                    .semantics { contentDescription = "$goToPage. $place" },
            )
        }
    }
}

/** Where the scrubber stands: the chapter, the page, and how far in. */
@Composable
private fun ScrubberPlace(chapter: String?, place: String, percent: String, spoken: String) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Space.xxs),
        modifier = Modifier
            .fillMaxWidth()
            // One statement rather than loose fragments. Read out separately,
            // "1%" follows "Page 1 of 100" as a label with no subject and means
            // nothing at all.
            .semantics(mergeDescendants = true) {
                contentDescription = spoken
            },
    ) {
        if (chapter != null) {
            Text(
                text = chapter,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.md),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = place,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                modifier = Modifier.weight(1f),
            )

            // How far in, which the page number alone does not say: page 120 is
            // a different place in a pamphlet than in a doorstop.
            Text(
                text = percent,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
