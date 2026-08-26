package io.lukia.features.library.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import io.lukia.shared.presentation.components.ClearGlyph
import io.lukia.shared.presentation.components.LukiaIconButton
import io.lukia.shared.presentation.components.SearchGlyph
import io.lukia.shared.presentation.components.SectionLabel
import io.lukia.shared.presentation.components.hoverHighlight
import io.lukia.shared.presentation.theme.LocalLukiaMetrics
import io.lukia.shared.presentation.theme.Space

/**
 * What has been searched for before, offered while the field is empty.
 *
 * This is the whole of what an empty search screen has to say. A reader looking
 * at one has either just arrived or just cleared the field, and in both cases
 * the useful thing is not a sentence explaining what searching is - it is the
 * words they have already found books with.
 *
 * Rows rather than chips. A search term is a phrase of unpredictable length, and
 * a wrapping row of chips of wildly different widths reads as clutter where a
 * column of left-aligned lines reads as a list.
 */
@Composable
internal fun RecentSearches(
    terms: List<String>,
    onSearch: (String) -> Unit,
    onForget: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Space.sm),
        ) {
            SectionLabel(text = "Recent Searches", modifier = Modifier.weight(1f))

            ClearAll(onClick = onClear)
        }

        terms.forEach { term ->
            RecentSearchRow(
                term = term,
                onClick = { onSearch(term) },
                onForget = { onForget(term) },
            )
        }
    }
}

/**
 * One remembered search: the word, and a way to drop it.
 *
 * The cross is its own control rather than a swipe. A swipe to delete is the
 * gesture a list of a reader's own content uses, and this is a list the app
 * wrote about them - it should be no harder to tidy than it was to fill.
 */
@Composable
private fun RecentSearchRow(
    term: String,
    onClick: () -> Unit,
    onForget: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = LocalLukiaMetrics.current
    val interactionSource = remember { MutableInteractionSource() }
    val shape = MaterialTheme.shapes.small

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.lg),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = metrics.rowHeight)
            .clip(shape)
            .hoverHighlight(interactionSource, shape)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClickLabel = "Search for $term",
                onClick = onClick,
            )
            .padding(vertical = Space.xs),
    ) {
        SearchGlyph(
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(metrics.iconSmall),
        )

        Text(
            text = term,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        LukiaIconButton(
            label = "Forget $term",
            onClick = onForget,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            size = metrics.iconButtonSmall,
            glyphSize = metrics.iconSmall,
        ) { tint, glyph ->
            ClearGlyph(
                tint = tint,
                knockout = MaterialTheme.colorScheme.background,
                modifier = glyph,
            )
        }
    }
}

/**
 * A word rather than a button. Emptying the history is not something a reader
 * came here to do, and a filled control saying so would compete with the list it
 * would delete.
 */
@Composable
private fun ClearAll(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = MaterialTheme.shapes.extraSmall

    Text(
        text = "Clear",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .clip(shape)
            .hoverHighlight(interactionSource, shape)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClickLabel = "Clear recent searches",
                onClick = onClick,
            )
            .padding(horizontal = Space.xs, vertical = Space.xxs),
    )
}
