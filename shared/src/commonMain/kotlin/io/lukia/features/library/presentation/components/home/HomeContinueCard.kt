package io.lukia.features.library.presentation.components.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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

/** How far the frosted panel is darkened, on top of its blur: enough for white text on a pale cover. */
private const val PanelDarkness = 0.5f

/** The panel's top corners; its bottom ones are the card's. */
private val PanelCorner: Dp = 20.dp

/** The heart and the server or device mark, drawn larger on the biggest cover on the screen. */
private val BadgeSize: Dp = 34.dp
private val MarkSize: Dp = 24.dp

/**
 * The book being read, at the top of the home screen, with a button to carry on.
 *
 * The cover is the card: drawn across the whole of it, cropped from the bottom
 * up so its title and picture stay, with how far through, the button and the
 * facts about the file on one dark panel over its lower part. No title or
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
    // How tall the panel along the bottom is, so the frosted cover behind it is
    // cut to the same shape.
    var panelHeight by remember { mutableIntStateOf(0) }

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

            FrostedPanel(book = book, title = title, panelHeight = { panelHeight })

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

            Overlay(
                book = book,
                onResume = { actions.onOpen(book) },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .onSizeChanged { panelHeight = it.height },
            )

            ContinueMenu(book, actions, menuExpanded, onMenuExpandedChange)
        }
    }
}

/**
 * The cover again behind the panel, frosted and cut to the panel's shape - so
 * the panel is glass over the cover rather than a slab across it.
 */
@Composable
private fun BoxScope.FrostedPanel(book: LibraryBook, title: String, panelHeight: () -> Int) {
    val corner = with(LocalDensity.current) { PanelCorner.toPx() }

    CoverBackground(
        cover = book.cover,
        title = title,
        frosted = true,
        modifier = Modifier
            .matchParentSize()
            .drawWithContent {
                val panel = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = 0f,
                            top = size.height - panelHeight(),
                            right = size.width,
                            bottom = size.height,
                            topLeftCornerRadius = CornerRadius(corner),
                            topRightCornerRadius = CornerRadius(corner),
                        ),
                    )
                }
                clipPath(panel) { this@drawWithContent.drawContent() }
            },
    )
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
 * How far through and the button on one line, the button at its end, then the
 * facts about the file - all in light colours whatever the theme, because they
 * are always on the dark panel rather than on the page.
 */
@Composable
private fun Overlay(book: LibraryBook, onResume: () -> Unit, modifier: Modifier = Modifier) {
    val onWash = MaterialTheme.colorScheme.copy(
        onSurface = Color.White,
        onSurfaceVariant = Color.White.copy(alpha = OnWashMuted),
        outlineVariant = Color.White.copy(alpha = OnWashLine),
    )

    MaterialTheme(colorScheme = onWash) {
        // One panel behind all three, so they read as one thing set on the
        // cover rather than as words floating over whatever the artwork is: the
        // frosted cover under it, darkened here. Flush with the card's sides
        // and bottom, whose corners clip it.
        Column(
            modifier = modifier
                .fillMaxWidth()
                .background(
                    color = Color.Black.copy(alpha = PanelDarkness),
                    shape = RoundedCornerShape(topStart = PanelCorner, topEnd = PanelCorner),
                )
                .padding(Space.lg),
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

            ContinueFacts(document = book.document, modifier = Modifier.padding(top = Space.md))
        }
    }
}

/** Secondary text on the panel: light, a step back from the white beside it. */
private const val OnWashMuted = 0.75f

/** Hairlines and the ring's track on the panel. */
private const val OnWashLine = 0.25f
