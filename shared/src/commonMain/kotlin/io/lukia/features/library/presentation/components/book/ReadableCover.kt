package io.lukia.features.library.presentation.components.book

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import io.lukia.core.domain.reading.ReadingProgress
import io.lukia.resources.Res
import io.lukia.resources.book_actions
import io.lukia.resources.book_read_title
import io.lukia.shared.presentation.components.controls.ReadingProgressOverlay
import io.lukia.shared.presentation.components.controls.focusRing
import io.lukia.shared.presentation.components.controls.menuGesture
import io.lukia.shared.presentation.components.cover.BookCover
import io.lukia.shared.presentation.components.cover.CoverArtSource
import io.lukia.shared.presentation.theme.LukiaElevation
import org.jetbrains.compose.resources.stringResource

/**
 * A book's cover, which is also a way into the book.
 *
 * The screen's Read button is the way in that says what it does, as Apple Books
 * and Play Books set theirs; the cover opens the book too, for the reader who
 * reaches for it, without a mark of its own over the artwork. A long press opens
 * the same actions menu the artwork carries elsewhere.
 *
 * Progress is drawn flush along the bottom edge, inside the cover's own corner
 * radius, so it reads as part of the artwork rather than a widget parked under
 * it. Shown whenever the book has been opened - it is information, not an
 * affordance.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ReadableCover(
    cover: CoverArtSource?,
    title: String,
    /** How far through. Null for a book that has never been opened. */
    progress: ReadingProgress?,
    enabled: Boolean,
    onRead: () -> Unit,
    onLongPress: () -> Unit,
    width: Dp,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = MaterialTheme.shapes.large

    Box(
        modifier = modifier
            .width(width)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .menuGesture(
                interactionSource = interactionSource,
                onClick = onRead,
                onMenu = onLongPress,
                menuLabel = stringResource(Res.string.book_actions),
                clickLabel = stringResource(Res.string.book_read_title, title),
                enabled = enabled,
                role = Role.Button,
            )
            .focusRing(interactionSource, shape)
            .semantics { contentDescription = title },
    ) {
        BookCover(
            cover = cover,
            title = title,
            shape = shape,
            elevation = LukiaElevation.feature,
            modifier = Modifier.fillMaxWidth(),
        )

        // What is laid over the artwork shares the cover's clip, so the progress
        // bar stops exactly where the rounded corners do.
        //
        // matchParentSize rather than fillMaxSize: the cover is what decides how
        // tall this is, through its aspect ratio, and a child that measured
        // itself against the parent would have nothing to measure against.
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape),
        ) {
            progress?.fraction?.let { fraction ->
                ReadingProgressOverlay(fraction = fraction, percent = progress.percent)
            }
        }
    }
}
