package io.lukia.features.library.presentation.components.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.core.presentation.authorLine
import io.lukia.core.presentation.bookTitle
import io.lukia.features.library.presentation.components.book.CoverTransferOverlay
import io.lukia.features.library.presentation.model.LibraryBook
import io.lukia.features.library.presentation.screens.BookTransfer
import io.lukia.features.library.presentation.sessions.HomeBookActions
import io.lukia.resources.Res
import io.lukia.resources.book_actions
import io.lukia.resources.book_read_title
import io.lukia.resources.book_title_by_author
import io.lukia.resources.home_resume
import io.lukia.shared.presentation.components.controls.ButtonSize
import io.lukia.shared.presentation.components.controls.LukiaButton
import io.lukia.shared.presentation.components.controls.menuGesture
import io.lukia.shared.presentation.components.cover.CoverBackground
import io.lukia.shared.presentation.components.cover.FavoriteBadge
import io.lukia.shared.presentation.components.menu.BookActionsMenu
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource

/** The card's width against its height: a cover cut short at the bottom, rather than a whole one. */
private const val CardAspectRatio = 4f / 5f

/**
 * No wider than this, however wide the window: past it a 4:5 card is taller
 * than the screen it is on, and the cover only gets softer as it is stretched.
 */
private val MaxCardWidth: Dp = 440.dp

/** How much of the card, from the bottom, the dark wash under the progress covers. */
private const val ScrimShare = 0.6f

/** The wash at its darkest, along the card's bottom edge. */
private const val ScrimDarkest = 0.88f

/**
 * The book being read, at the top of the home screen, with a button to carry on.
 *
 * The cover is the card: drawn across the whole of it, cropped from the bottom
 * up so its title and picture stay, with how far through, the button and the
 * facts about the file laid over a dark wash along its lower part. No title or
 * author is printed - at this size the cover says what the book is - but a
 * screen reader is told both.
 *
 * The press and the long press work as on every other cover: anywhere on the
 * card opens the book, and the long press offers the book's menu, which is
 * also where its own page is.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HomeContinueCard(
    book: LibraryBook,
    actions: HomeBookActions,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /** How a download of this book is going, if one is - see [CoverTransferOverlay]. */
    transfer: BookTransfer? = null,
) {
    val title = book.document.bookTitle()
    val authors = book.document.metadata?.authorLine()
    val named = if (authors != null) stringResource(Res.string.book_title_by_author, title, authors) else title
    val shape = MaterialTheme.shapes.extraLarge
    val interactionSource = remember { MutableInteractionSource() }

    BoxWithConstraints(modifier = modifier.widthIn(max = MaxCardWidth)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // The card's shape at least; taller only when large text needs it.
                .heightIn(min = maxWidth / CardAspectRatio)
                .clip(shape)
                .menuGesture(
                    interactionSource = interactionSource,
                    onClick = { actions.onOpen(book) },
                    onMenu = { onMenuExpandedChange(true) },
                    menuLabel = stringResource(Res.string.book_actions),
                    clickLabel = stringResource(Res.string.book_read_title, title),
                )
                .pointerHoverIcon(PointerIcon.Hand)
                .semantics { contentDescription = named },
        ) {
            CoverBackground(cover = book.cover, title = title, modifier = Modifier.matchParentSize())

            Scrim(modifier = Modifier.matchParentSize())

            CoverTransferOverlay(
                document = book.document,
                transfer = transfer,
                modifier = Modifier.matchParentSize(),
            )

            FavoriteBadge(
                visible = book.document.isFavorite,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(Space.md),
            )

            Overlay(
                book = book,
                onResume = { actions.onOpen(book) },
                modifier = Modifier.align(Alignment.BottomStart),
            )

            BookActionsMenu(
                expanded = menuExpanded,
                isFavorite = book.document.isFavorite,
                onToggleFavorite = { actions.onToggleFavorite(book) },
                onRefresh = { actions.onRefresh(book) },
                onDelete = { actions.onDelete(book) },
                canRemoveFromDevice = !book.document.awaitsDownload,
                onDismissRequest = { onMenuExpandedChange(false) },
                onShowDetails = { actions.onShowDetails(book) },
                onSelectBooks = actions.onSelectBooks,
                onRemoveFromServer = actions.removeFromServer(book.document),
                onMarkUnread = book.progress?.let { { actions.onMarkUnread(book) } },
            )
        }
    }
}

/** Darkens the lower part of the cover, so what is laid over it reads on any artwork. */
@Composable
private fun Scrim(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                0f to Color.Transparent,
                (1f - ScrimShare) to Color.Transparent,
                1f to Color.Black.copy(alpha = ScrimDarkest),
            ),
        ),
    )
}

/**
 * How far through and the button on one line, the button at its end, then the
 * facts about the file - all in light colours whatever the theme, because they
 * are always on the dark wash rather than on the page.
 */
@Composable
private fun Overlay(book: LibraryBook, onResume: () -> Unit, modifier: Modifier = Modifier) {
    val onWash = MaterialTheme.colorScheme.copy(
        onSurface = Color.White,
        onSurfaceVariant = Color.White.copy(alpha = OnWashMuted),
        outlineVariant = Color.White.copy(alpha = OnWashLine),
    )

    MaterialTheme(colorScheme = onWash) {
        Column(modifier = modifier.fillMaxWidth().padding(Space.lg)) {
            // Where the two do not both fit - a narrow phone, a large font size -
            // the button moves under the progress rather than squeezing it.
            FlowRow(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(Space.sm),
                itemVerticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                book.progress?.let { progress -> ContinueProgress(progress) }

                LukiaButton(
                    label = stringResource(Res.string.home_resume),
                    onClick = onResume,
                    size = ButtonSize.Compact,
                )
            }

            ContinueFacts(document = book.document, modifier = Modifier.padding(top = Space.md))
        }
    }
}

/** Secondary text on the wash: light, a step back from the white beside it. */
private const val OnWashMuted = 0.75f

/** Hairlines and the ring's track on the wash. */
private const val OnWashLine = 0.25f
