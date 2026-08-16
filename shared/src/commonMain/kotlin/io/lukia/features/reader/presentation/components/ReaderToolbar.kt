package io.lukia.features.reader.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.shared.presentation.components.BackChevron
import io.lukia.shared.presentation.components.DisclosureChevron
import io.lukia.shared.presentation.components.GlassSurface

/** Apple's minimum comfortable target, and the height of the bar itself. */
private val ControlSize: Dp = 44.dp

/**
 * The reader's chrome: leaving the book, where you are in it, and how it reads.
 *
 * A capsule that takes its own row rather than floating over the page. Two
 * reasons: nothing ever passes underneath it, so the page is never partly
 * covered by the thing telling you which page it is; and with no moving content
 * behind it the translucency costs nothing to draw.
 *
 * [GlassSurface] resolves per platform on its own - the translucent, specular
 * treatment on Apple platforms and a flatter elevated surface elsewhere - so the
 * bar is native-looking on iOS without the other platforms being handed an iOS
 * look. See `GlassStyle` for why that is a drawn approximation rather than a
 * system material.
 */
@Composable
internal fun ReaderToolbar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    centerControl: @Composable () -> Unit,
    endControl: @Composable () -> Unit
) {
    GlassSurface(
        shape = RoundedCornerShape(percent = 50),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .height(ControlSize)
        ) {
            ToolbarButton(
                onClick = onBack,
                label = "Close book"
            ) {
                BackChevron(
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            centerControl()

            endControl()
        }
    }
}

/**
 * Where the reader is in the book, and the way to somewhere else in it.
 *
 * The page number is read inside this composable and nowhere else, so turning a
 * page recomposes this label rather than the bar or the pages around it. The
 * `derivedStateOf` is what keeps that to once per page instead of once per
 * scrolled pixel.
 *
 * It doubles as the button for the contents panel: it is already where the eye
 * goes to find out where it is in the book, and it costs the toolbar no width to
 * make it also the way to move.
 */
@Composable
internal fun PageIndicatorButton(
    listState: LazyListState,
    pageCount: Int,
    onClick: () -> Unit
) {
    val currentPage by remember(listState) {
        derivedStateOf { listState.firstVisibleItemIndex + 1 }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClickLabel = "Open contents", onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp)
            // "12 / 348" is a clear enough label to look at and a poor one to
            // hear, so assistive technology is given the sentence instead.
            //
            // Deliberately not a live region: this changes on every page, and
            // announcing each one would talk over a reader scrolling through a
            // long book. The pages themselves carry the position instead.
            .semantics { contentDescription = "Page $currentPage of $pageCount" }
    ) {
        Text(
            text = "$currentPage / $pageCount",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        DisclosureChevron(
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(start = 5.dp)
                .size(9.dp)
        )
    }
}

/**
 * A square, comfortably tappable target for one piece of toolbar chrome.
 *
 * [label] is set as a content description as well as the click label. These
 * buttons draw a glyph and no text, so without the description a screen reader
 * has nothing to announce them by - the click label names the action, not the
 * control it belongs to.
 */
@Composable
internal fun ToolbarButton(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClickLabel = label, onClick = onClick)
            .size(ControlSize)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
