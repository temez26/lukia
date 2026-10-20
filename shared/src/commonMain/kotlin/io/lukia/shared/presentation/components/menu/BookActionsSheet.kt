package io.lukia.shared.presentation.components.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.lukia.resources.Res
import io.lukia.resources.menu_book_details
import io.lukia.resources.menu_favourite
import io.lukia.resources.menu_mark_unread
import io.lukia.resources.menu_refresh_details
import io.lukia.resources.menu_remove_from_device
import io.lukia.resources.menu_remove_from_server
import io.lukia.resources.menu_select_books
import io.lukia.resources.menu_unfavourite
import io.lukia.resources.reader_percent
import io.lukia.resources.sheet_details
import io.lukia.resources.sheet_favourite
import io.lukia.resources.sheet_select
import io.lukia.resources.sheet_unread
import io.lukia.shared.presentation.components.controls.pressable
import io.lukia.shared.presentation.components.cover.BookCover
import io.lukia.shared.presentation.components.cover.CoverArtSource
import io.lukia.shared.presentation.components.icons.CheckGlyph
import io.lukia.shared.presentation.components.icons.HeartGlyph
import io.lukia.shared.presentation.components.icons.InfoGlyph
import io.lukia.shared.presentation.components.icons.RefreshGlyph
import io.lukia.shared.presentation.components.icons.TrashGlyph
import io.lukia.shared.presentation.components.icons.UnreadBookGlyph
import io.lukia.shared.presentation.theme.Space
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** The book a menu was opened on, as the sheet's header shows it. */
@Immutable
class BookMenuHeader(
    val title: String,
    val author: String?,
    val cover: CoverArtSource?,
    /** How far through, or null for a book not started or of unknown length. */
    val percent: Int?,
)

/** Everything the book menu offers; a null action is a row or button that is not there. */
@Immutable
internal class BookMenuChoices(
    val isFavorite: Boolean,
    val onToggleFavorite: () -> Unit,
    val onRefresh: () -> Unit,
    val onDelete: () -> Unit,
    val onShowDetails: (() -> Unit)? = null,
    val onSelectBooks: (() -> Unit)? = null,
    val onRemoveFromServer: (() -> Unit)? = null,
    val onMarkUnread: (() -> Unit)? = null,
    val canRemoveFromDevice: Boolean = true,
)

private val HeaderCoverWidth: Dp = 40.dp
private val QuickActionSize: Dp = 48.dp
private val QuickGlyphSize: Dp = 22.dp

/**
 * The book menu as a phone opens it: a sheet up from the bottom of the screen,
 * where a thumb already is, rather than a small menu hung off a cover.
 *
 * The book it is about at the top, then the four things most often done to it
 * as round buttons in a row - favourite, its page, unread, select - and under
 * them, as rows, refreshing it and the ones that take it away, in red.
 *
 * Dimmed behind by the screen's own scrim, which every screen with a book menu
 * already draws while one is open; the sheet's own would darken it twice.
 * Each choice slides the sheet away first and acts once it has gone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookActionsSheet(
    expanded: Boolean,
    choices: BookMenuChoices,
    onDismissRequest: () -> Unit,
    header: BookMenuHeader?,
) {
    if (!expanded) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val choose: (() -> Unit) -> () -> Unit = { action ->
        {
            scope.launch { sheetState.hide() }.invokeOnCompletion {
                onDismissRequest()
                action()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        scrimColor = Color.Transparent,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(horizontal = Space.lg).padding(bottom = Space.lg)) {
            header?.let {
                SheetHeader(it)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            QuickActions(choices = choices, choose = choose)

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            MenuRow(
                label = stringResource(Res.string.menu_refresh_details),
                icon = { tint, glyph -> RefreshGlyph(tint = tint, modifier = glyph) },
                onClick = choose(choices.onRefresh),
            )

            if (choices.canRemoveFromDevice) {
                RemoveRow(stringResource(Res.string.menu_remove_from_device), choose(choices.onDelete))
            }

            choices.onRemoveFromServer?.let { remove ->
                RemoveRow(stringResource(Res.string.menu_remove_from_server), choose(remove))
            }
        }
    }
}

@Composable
private fun SheetHeader(header: BookMenuHeader) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.md),
        modifier = Modifier.padding(bottom = Space.md),
    ) {
        BookCover(
            cover = header.cover,
            title = header.title,
            shape = MaterialTheme.shapes.extraSmall,
            monogramFontSize = 16.sp,
            modifier = Modifier.width(HeaderCoverWidth),
        )

        Column {
            Text(
                text = header.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            val detail = listOfNotNull(
                header.author,
                header.percent?.let { stringResource(Res.string.reader_percent, it) },
            )
            if (detail.isNotEmpty()) {
                Text(
                    text = detail.joinToString(DetailSeparator),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Separates the author from how far through, as the rest of the app sets such facts. */
private const val DetailSeparator = " · "

/** The four most-used choices as round buttons, each where the caller offers it. */
@Composable
private fun QuickActions(choices: BookMenuChoices, choose: (() -> Unit) -> () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = Space.md)) {
        QuickAction(
            label = stringResource(Res.string.sheet_favourite),
            spoken = stringResource(
                if (choices.isFavorite) Res.string.menu_unfavourite else Res.string.menu_favourite,
            ),
            onClick = choose(choices.onToggleFavorite),
        ) { tint, glyph -> HeartGlyph(tint = tint, filled = choices.isFavorite, modifier = glyph) }

        choices.onShowDetails?.let { show ->
            QuickAction(
                label = stringResource(Res.string.sheet_details),
                spoken = stringResource(Res.string.menu_book_details),
                onClick = choose(show),
            ) { tint, glyph -> InfoGlyph(tint = tint, modifier = glyph) }
        }

        choices.onMarkUnread?.let { mark ->
            QuickAction(
                label = stringResource(Res.string.sheet_unread),
                spoken = stringResource(Res.string.menu_mark_unread),
                onClick = choose(mark),
            ) { tint, glyph -> UnreadBookGlyph(tint = tint, modifier = glyph) }
        }

        choices.onSelectBooks?.let { select ->
            QuickAction(
                label = stringResource(Res.string.sheet_select),
                spoken = stringResource(Res.string.menu_select_books),
                onClick = choose(select),
            ) { tint, glyph -> CheckGlyph(tint = tint, modifier = glyph) }
        }
    }
}

/**
 * A round button with a short word under it. Read out by the menu's own longer
 * wording - "Add to Favourites" rather than "Favourite" - which says what pressing
 * it does where the word under it only names it.
 */
@Composable
private fun RowScope.QuickAction(
    label: String,
    spoken: String,
    onClick: () -> Unit,
    glyph: @Composable (Color, Modifier) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = MaterialTheme.shapes.medium

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.xs),
        modifier = Modifier
            .weight(1f)
            .clip(shape)
            .pressable(interactionSource = interactionSource, shape = shape, onClickLabel = spoken, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
                onClick(label = spoken) {
                    onClick()
                    true
                }
            }
            .padding(vertical = Space.xs),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(QuickActionSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            glyph(MaterialTheme.colorScheme.primary, Modifier.size(QuickGlyphSize))
        }

        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A row that takes the book away from somewhere: red, with a bin. */
@Composable
private fun RemoveRow(label: String, onClick: () -> Unit) {
    MenuRow(
        label = label,
        tint = MaterialTheme.colorScheme.error,
        icon = { tint, glyph -> TrashGlyph(tint = tint, modifier = glyph) },
        onClick = onClick,
    )
}
