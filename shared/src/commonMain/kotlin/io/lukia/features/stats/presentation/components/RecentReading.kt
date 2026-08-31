package io.lukia.features.stats.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.lukia.core.domain.reading.ReadingProgress
import io.lukia.core.presentation.displayTitle
import io.lukia.features.stats.domain.ReadingActivity
import io.lukia.shared.presentation.components.controls.ReadingProgressBar
import io.lukia.shared.presentation.components.controls.focusRing
import io.lukia.shared.presentation.components.controls.hoverHighlight
import io.lukia.shared.presentation.components.cover.BookCover
import io.lukia.shared.presentation.components.cover.rememberBookCover
import io.lukia.shared.presentation.components.surface.LukiaDivider
import io.lukia.shared.presentation.theme.Space

private val ActivityCoverHeight: Dp = 56.dp

private val ActivityCoverWidth: Dp = 38.dp

/** Thinner than the bar welded to a cover: this one has a row to itself. */
private val ActivityBarHeight: Dp = 3.dp

/**
 * The books the reader has been in, newest first.
 *
 * A list rather than a shelf of covers, because what makes a row here worth
 * looking at is the number on it: this is the answer to "where was I", and a
 * cover alone cannot say "page 88 of 210". Each row opens the book, which is the
 * only thing anybody wants from this list.
 */
@Composable
internal fun RecentReadingList(
    activity: List<ReadingActivity>,
    onOpenBook: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        activity.forEachIndexed { index, entry ->
            if (index > 0) {
                // Inset past the cover, so the rules line up under the titles
                // rather than cutting across the artwork.
                LukiaDivider(modifier = Modifier.padding(start = ActivityCoverWidth + Space.lg))
            }

            ActivityRow(entry = entry, onClick = { onOpenBook(entry.location) })
        }
    }
}

@Composable
private fun ActivityRow(
    entry: ReadingActivity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = entry.document.displayTitle()
    val interactionSource = remember { MutableInteractionSource() }
    val shape = MaterialTheme.shapes.medium

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .hoverHighlight(interactionSource, shape)
            .focusRing(interactionSource, shape)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClickLabel = "Open $title",
                onClick = onClick,
            )
            .padding(vertical = Space.md),
    ) {
        BookCover(
            cover = rememberBookCover(entry.location, entry.document.coverImage?.bytes),
            title = title,
            monogramFontSize = 13.sp,
            modifier = Modifier.size(ActivityCoverWidth, ActivityCoverHeight),
        )

        ActivityDetails(
            title = title,
            progress = entry.progress,
            modifier = Modifier
                .weight(1f)
                .padding(start = Space.lg),
        )
    }
}

/**
 * What the row says: the book, where in it, and how far that is.
 *
 * The page is spelled out beside the percentage rather than instead of it. A
 * percentage says how much is left and a page number says where to open to, and
 * somebody looking at this list wants both - which is also why the bar runs the
 * width of the row here rather than sitting in a card's corner.
 */
@Composable
private fun ActivityDetails(
    title: String,
    progress: ReadingProgress,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text = progressLine(progress),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )

        progress.fraction?.let { fraction ->
            ReadingProgressBar(
                fraction = fraction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ActivityBarHeight),
            )
        }
    }
}

/**
 * Where the reader is, in words.
 *
 * A book whose length is unknown - a position stored before the app knew how
 * long the document was - says only that it has been opened, rather than
 * inventing a page count to divide by.
 */
private fun progressLine(progress: ReadingProgress): String {
    val page = progress.pageNumber ?: return "Opened"

    return when {
        progress.isFinished -> "Finished · ${progress.pageCount} pages"
        else -> "Page $page of ${progress.pageCount} · ${progress.percent}%"
    }
}
