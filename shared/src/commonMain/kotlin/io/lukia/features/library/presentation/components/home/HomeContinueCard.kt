package io.lukia.features.library.presentation.components.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import io.lukia.shared.presentation.components.cover.BOOK_COVER_ASPECT_RATIO
import io.lukia.shared.presentation.components.cover.CoverBackground
import io.lukia.shared.presentation.components.cover.FavoriteBadge
import io.lukia.shared.presentation.components.menu.BookActionsMenu
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource

/**
 * No wider than this, however wide the window: past it a whole cover is taller
 * than the screen it is on, and it only gets softer as it is stretched.
 */
private val MaxCardWidth: Dp = 400.dp

/**
 * The band's colour: solid, so nothing of the cover's lettering shows through
 * behind the text, and near-black whatever the theme, so white text and the
 * accent button read the same over any cover.
 */
private val PanelColor = Color(0xFF141416)

/** The heart and the server or device mark, drawn larger on the biggest cover on the screen. */
private val BadgeSize: Dp = 34.dp
private val MarkSize: Dp = 24.dp

/**
 * The book being read, at the top of the home screen, with a button to carry on.
 *
 * The cover is the card, whole and at its own proportions, with how far through
 * and the button on a solid band joined to its bottom edge, and the facts about
 * the file under the card. No title or author is printed:
 * at this size the cover says what the book is, and a screen reader is told
 * both.
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

    Column(modifier = modifier.widthIn(max = MaxCardWidth)) {
        // The cover and the band under it are one card: one shape, one press.
        Column(
            modifier = Modifier
                .fillMaxWidth()
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
            ContinueCover(
                book = book,
                title = title,
                actions = actions,
                menuExpanded = menuExpanded,
                onMenuExpandedChange = onMenuExpandedChange,
                transfer = transfer,
            )

            Band(book = book, onResume = { actions.onOpen(book) })
        }

        // Under the card, on the page, so the band stays one line.
        ContinueFacts(document = book.document, modifier = Modifier.padding(top = Space.md))
    }
}

/** The cover whole, at its own proportions, with its badges and the menu a long press opens. */
@Composable
private fun ContinueCover(
    book: LibraryBook,
    title: String,
    actions: HomeBookActions,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    transfer: BookTransfer?,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // The cover's own shape, so none of it is cropped.
            .aspectRatio(BOOK_COVER_ASPECT_RATIO),
    ) {
        CoverBackground(cover = book.cover, title = title, modifier = Modifier.matchParentSize())

        CoverTransferOverlay(
            document = book.document,
            transfer = transfer,
            markSize = MarkSize,
            modifier = Modifier.matchParentSize(),
        )

        FavoriteBadge(
            visible = book.document.isFavorite,
            size = BadgeSize,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(Space.md),
        )

        ContinueMenu(book, actions, menuExpanded, onMenuExpandedChange)
    }
}

/** The menu a long press opens, the same as every other cover's. */
@Composable
private fun ContinueMenu(
    book: LibraryBook,
    actions: HomeBookActions,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    BookActionsMenu(
        expanded = expanded,
        isFavorite = book.document.isFavorite,
        onToggleFavorite = { actions.onToggleFavorite(book) },
        onRefresh = { actions.onRefresh(book) },
        onDelete = { actions.onDelete(book) },
        canRemoveFromDevice = !book.document.awaitsDownload,
        onDismissRequest = { onExpandedChange(false) },
        onShowDetails = { actions.onShowDetails(book) },
        onSelectBooks = actions.onSelectBooks,
        onRemoveFromServer = actions.removeFromServer(book.document),
        onMarkUnread = book.progress?.let { { actions.onMarkUnread(book) } },
    )
}

/**
 * How far through and the button on one line, the button at its end, on the
 * band under the cover - in light colours whatever the theme, because the band
 * is always dark.
 */
@Composable
private fun Band(book: LibraryBook, onResume: () -> Unit, modifier: Modifier = Modifier) {
    val onWash = MaterialTheme.colorScheme.copy(
        onSurface = Color.White,
        onSurfaceVariant = Color.White.copy(alpha = OnWashMuted),
        outlineVariant = Color.White.copy(alpha = OnWashLine),
    )

    MaterialTheme(colorScheme = onWash) {
        // Flush with the card's sides and bottom, whose corners clip it.
        Column(
            modifier = modifier
                .fillMaxWidth()
                .background(PanelColor)
                .padding(horizontal = Space.lg, vertical = Space.md),
        ) {
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
        }
    }
}

/** Secondary text on the band: light, a step back from the white beside it. */
private const val OnWashMuted = 0.75f

/** The ring's track on the band. */
private const val OnWashLine = 0.25f
