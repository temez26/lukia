package io.lukia.features.library.presentation.components.book

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.zIndex
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.presentation.displayTitle
import io.lukia.core.presentation.displayType
import io.lukia.core.presentation.formattedSize
import io.lukia.features.library.presentation.components.selection.CardSelection
import io.lukia.features.library.presentation.components.selection.jiggleAngle
import io.lukia.features.library.presentation.model.LibraryBook
import io.lukia.resources.Res
import io.lukia.resources.book_actions
import io.lukia.resources.book_favourited
import io.lukia.resources.book_format_and_size
import io.lukia.resources.book_unavailable
import io.lukia.resources.reader_percent
import io.lukia.resources.state_not_selected
import io.lukia.resources.state_selected
import io.lukia.shared.presentation.components.controls.ReadingProgressOverlay
import io.lukia.shared.presentation.components.controls.focusRing
import io.lukia.shared.presentation.components.controls.hoverLift
import io.lukia.shared.presentation.components.controls.menuGesture
import io.lukia.shared.presentation.components.controls.rememberHoverFraction
import io.lukia.shared.presentation.components.cover.BookCover
import io.lukia.shared.presentation.components.launch.LaunchReveal
import io.lukia.shared.presentation.components.launch.launchReveal
import io.lukia.shared.presentation.components.surface.StatusBadge
import io.lukia.shared.presentation.theme.LukiaElevation
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource

/**
 * One book in the library grid.
 *
 * Menu open state is owned by the grid rather than the card, so opening one menu
 * closes any other; the card only reports that a menu was asked for.
 *
 * The card carries its feedback as motion rather than as a ripple: it shrinks
 * under a press and, on desktop, lifts and casts a deeper shadow under the
 * pointer. A ripple washing across a book cover reads as a defect in the artwork.
 *
 * Nothing here clips. The cover casts its shadow deliberately outside its own
 * bounds, and a clip anywhere up the chain crops that shadow to a hard edge -
 * which is what used to make a hovered card look as though its shadow had been
 * cut off square, more visibly the further it lifted.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BookCard(
    book: LibraryBook,
    menuExpanded: Boolean,
    onClick: () -> Unit,
    onMenuRequest: () -> Unit,
    onMenuDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
    onShowDetails: () -> Unit,
    onSelectBooks: () -> Unit,
    reveal: LaunchReveal,
    revealIndex: Int,
    modifier: Modifier = Modifier,
    /** How this card takes part in selection, or null when nothing is being selected. */
    selection: CardSelection? = null,
) {
    val document = book.document
    val title = document.displayTitle()

    val interactionSource = remember { MutableInteractionSource() }
    val hover = rememberHoverFraction(interactionSource)
    val coverShape = MaterialTheme.shapes.medium
    // State, read inside the layer below rather than here - see jiggleAngle.
    val angle = jiggleAngle(active = selection != null, seed = revealIndex)

    Column(
        modifier = modifier
            .bookCardSemantics(book = book, title = title, selection = selection)
            .bookCardGestures(
                interactionSource = interactionSource,
                hover = hover,
                reveal = reveal,
                revealIndex = revealIndex,
                // While the shelf is being selected from, a press is a tick
                // rather than a way into the book, and the menu is not offered
                // at all - the bar above is where the actions are now.
                onClick = selection?.onToggle ?: onClick,
                onMenuRequest = if (selection == null) onMenuRequest else selection.onToggle,
            )
            // Its own layer rather than folded into the one the gestures use.
            // That one is shared by hover and press and is there on every card
            // always; this exists only while a shelf is being selected from, and
            // costs nothing the rest of the time.
            .graphicsLayer { rotationZ = angle.value },
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            BookCover(
                cover = book.cover,
                title = title,
                shape = coverShape,
                // Deepens as the card lifts, so the two read as one movement
                // rather than as a card that floats without leaving the page.
                elevation = lerp(LukiaElevation.card, LukiaElevation.cardHovered, hover),
                modifier = Modifier
                    .fillMaxWidth()
                    // The card is what the keyboard focuses, and the cover is the
                    // part of it worth ringing.
                    .focusRing(interactionSource, coverShape),
            )

            // Clipped to the cover's own shape so the bar and the badge stop
            // where the rounded corners do, and drawn only for a book somebody
            // has started - an empty bar on every unread cover would turn a
            // shelf into a progress report.
            book.progress?.fraction?.let { fraction ->
                ReadingProgressOverlay(
                    fraction = fraction,
                    percent = book.progress?.percent,
                    modifier = Modifier.matchParentSize().clip(coverShape),
                )
            }

            CoverOverlays(
                document = document,
                hover = hover,
                menuExpanded = menuExpanded,
                selection = selection,
                onMenuRequest = onMenuRequest,
                onMenuDismiss = onMenuDismiss,
                onToggleFavorite = onToggleFavorite,
                onRefresh = onRefresh,
                onDelete = onDelete,
                onShowDetails = onShowDetails,
                onSelectBooks = onSelectBooks,
            )
        }

        BookCardCaption(document = document, title = title)
    }
}

/**
 * What the card is, said once for the whole of it.
 *
 * A cover, a heart, a tick and a progress bar are four drawn marks and no words.
 * What a shelf being picked from used to announce was the title and the file's
 * size - with nothing at all to say whether this book was one of the ones
 * ticked, and with the heart marking a favourite reachable only by opening the
 * menu on every cover in turn to read the row that toggles it.
 *
 * Merged, because a card is one thing: a title, a format, a size and a badge
 * read out as four stops is four swipes per book down a shelf of two hundred.
 */
@Composable
private fun Modifier.bookCardSemantics(
    book: LibraryBook,
    title: String,
    selection: CardSelection?,
): Modifier {
    val document = book.document

    // Read before the modifier chain: `semantics` is not a composable scope.
    val state = stringResource(
        if (selection?.selected == true) Res.string.state_selected else Res.string.state_not_selected,
    )

    val spoken = listOfNotNull(
        title,
        stringResource(Res.string.book_favourited).takeIf { document.isFavorite },
        book.progress?.percent?.let { stringResource(Res.string.reader_percent, it) },
        stringResource(Res.string.book_unavailable).takeIf { !document.exists },
    ).joinToString(separator = ". ")

    return semantics(mergeDescendants = true) {
        contentDescription = spoken

        // A card that is being picked from is a tick rather than a way into the
        // book, and has to say so as well as draw so.
        selection?.let {
            selected = it.selected
            role = Role.Checkbox
            stateDescription = state
        }
    }
}

/**
 * How a card answers a pointer and a finger: it lifts and deepens its shadow
 * under the one, shrinks under a press from either, and opens its menu on a long
 * press.
 *
 * One `graphicsLayer` for both gestures - see [hoverLift]. Two stacked ones
 * meant the press scaled a layer that was already scaling for hover, and each
 * added a compositing pass the grid paid for on every frame.
 */
@Composable
private fun Modifier.bookCardGestures(
    interactionSource: MutableInteractionSource,
    hover: Float,
    reveal: LaunchReveal,
    revealIndex: Int,
    onClick: () -> Unit,
    onMenuRequest: () -> Unit,
): Modifier = this
    // Raised above its neighbours while it is lifted, so the deepened shadow
    // is not painted over by the next cell in the row.
    .zIndex(if (hover > 0f) 1f else 0f)
    .launchReveal(reveal, index = revealIndex)
    .hoverLift(
        interactionSource = interactionSource,
        fraction = hover,
        lift = 4.dp,
        hoveredScale = 1.03f,
        pressedScale = 0.97f,
    )
    .pointerHoverIcon(PointerIcon.Hand)
    .menuGesture(
        interactionSource = interactionSource,
        onClick = onClick,
        onMenu = onMenuRequest,
        menuLabel = stringResource(Res.string.book_actions),
    )
    // Leaves the cover's shadow somewhere to fall inside the cell rather
    // than straight onto the neighbouring one.
    .padding(Space.xs)

/**
 * What is written under the cover: the title, what kind of file it is and how
 * big, and a mark for a book whose file has gone missing.
 */
@Composable
private fun BookCardCaption(
    document: DocumentInfo,
    title: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            // Reserving two lines keeps the metadata row aligned across the
            // grid whether a title wraps or not.
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Space.md),
        )

        Text(
            text = stringResource(
                Res.string.book_format_and_size,
                document.displayType(),
                document.formattedSize(),
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Space.xxs),
        )

        if (!document.exists) {
            StatusBadge(
                text = stringResource(Res.string.book_unavailable),
                modifier = Modifier.padding(top = Space.sm),
            )
        }
    }
}
