package io.lukia.features.reader.presentation.components.contents

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.features.reader.presentation.ChapterRow
import io.lukia.resources.Res
import io.lukia.resources.reader_chapter_at_page
import io.lukia.resources.reader_go_to_chapter
import io.lukia.resources.reader_hide_sections_of
import io.lukia.resources.reader_show_sections_of
import io.lukia.shared.presentation.components.controls.pressable
import io.lukia.shared.presentation.components.icons.DisclosureChevron
import io.lukia.shared.presentation.theme.HairlineWidth
import io.lukia.shared.presentation.theme.LocalLukiaMetrics
import io.lukia.shared.presentation.theme.LukiaAlpha
import io.lukia.shared.presentation.theme.LukiaMotion
import io.lukia.shared.presentation.theme.MinTouchTarget
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource

/** Indent per level of nesting. One step is the width of the disclosure control. */
private val LevelIndent: Dp = 16.dp

/**
 * How wide the slot holding a row's fold control is.
 *
 * The chevron drawn in it is 10dp, and the slot was sized to the chevron - which
 * made the one control in the contents panel that is pressed rather than read a
 * 22dp target, under the 24dp floor a pointer target has and half of what a
 * thumb is given everywhere else in this app. The chevron has not changed size;
 * the room around it has.
 */
private val DisclosureSlot: Dp = MinTouchTarget

/** Room for a four-digit page number, so the column of them is straight. */
private val PageNumberWidth: Dp = 32.dp

/** The marker down the start edge of the chapter the reader is in. */
private val CurrentMarkerWidth: Dp = 3.dp

// One row of the table of contents: its indent, its disclosure control, its
// page number, and the marker showing the chapter the reader is in. The
// widest part of the list, and the only part that draws anything itself.

/**
 * One entry in the contents.
 *
 * Chapter against section is carried three ways over, because indentation alone
 * is not legible in a panel this narrow: a chapter is set in a heavier, larger
 * face; every level of nesting adds a hairline guide down the start edge, so a
 * section visibly hangs off something; and only a chapter with sections carries
 * the control that folds them away.
 */
@Composable
internal fun ChapterRowItem(
    row: ChapterRow,
    inset: Dp,
    isCurrent: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit,
) {
    val entry = row.entry
    val isChapter = entry.level == 0
    val guideColor = MaterialTheme.colorScheme.outlineVariant
    val markerColor = MaterialTheme.colorScheme.primary
    val metrics = LocalLukiaMetrics.current
    val interactionSource = remember { MutableInteractionSource() }

    // Read before the modifier chain: `semantics` is not a composable scope.
    val goTo = stringResource(Res.string.reader_go_to_chapter, entry.title)
    val spoken = stringResource(
        Res.string.reader_chapter_at_page,
        entry.title,
        entry.pageIndex + 1,
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isCurrent) {
                    MaterialTheme.colorScheme.primary.copy(alpha = LukiaAlpha.Selected)
                } else {
                    Color.Transparent
                },
            )
            .drawBehind {
                if (isCurrent) {
                    drawRoundRect(
                        color = markerColor,
                        topLeft = Offset(0f, size.height * 0.18f),
                        size = Size(CurrentMarkerWidth.toPx(), size.height * 0.64f),
                        cornerRadius = CornerRadius(CurrentMarkerWidth.toPx()),
                    )
                }

                // One guide per level between this entry and the top, so a
                // sub-section reads as hanging off its section as well as off
                // the chapter above them both.
                repeat(entry.level) { level ->
                    val x = (inset + LevelIndent * level + DisclosureSlot / 2).toPx()

                    drawLine(
                        color = guideColor,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = HairlineWidth.toPx(),
                    )
                }
            }
            .pressable(
                interactionSource = interactionSource,
                shape = RectangleShape,
                onClickLabel = goTo,
                onClick = onClick,
            )
            .heightIn(min = metrics.rowHeight)
            .padding(start = inset + LevelIndent * entry.level, end = inset)
            .semantics { contentDescription = spoken },
    ) {
        if (row.hasChildren) {
            DisclosureToggle(
                collapsed = row.isCollapsed,
                title = entry.title,
                rowHeight = metrics.rowHeight,
                onClick = onToggle,
            )
        } else {
            // Kept empty rather than closed up, so titles line up down the panel
            // whether or not their row has anything to fold.
            Box(modifier = Modifier.width(DisclosureSlot))
        }

        Text(
            text = entry.title,
            style = if (isChapter) {
                MaterialTheme.typography.bodyMedium
            } else {
                MaterialTheme.typography.bodySmall
            },
            fontWeight = if (isChapter) FontWeight.SemiBold else FontWeight.Normal,
            color = when {
                isCurrent -> MaterialTheme.colorScheme.primary
                isChapter -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = Space.sm),
        )

        Text(
            text = "${entry.pageIndex + 1}",
            style = MaterialTheme.typography.labelSmall,
            color = if (isCurrent) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier
                .padding(start = Space.md)
                .widthIn(min = PageNumberWidth),
        )
    }
}

/**
 * Folds a chapter's sections away, and says on sight whether they are folded.
 *
 * Its own click target inside the row: pressing the title goes to the chapter,
 * which is what a reader who wants the chapter means, and pressing this shows
 * what is inside it without leaving the panel.
 */
@Composable
private fun DisclosureToggle(
    collapsed: Boolean,
    title: String,
    rowHeight: Dp,
    onClick: () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (collapsed) -90f else 0f,
        animationSpec = LukiaMotion.gentle(),
        label = "chapterDisclosure",
    )

    val label = stringResource(
        if (collapsed) Res.string.reader_show_sections_of else Res.string.reader_hide_sections_of,
        title,
    )
    val interactionSource = remember { MutableInteractionSource() }
    val shape = MaterialTheme.shapes.extraSmall

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = DisclosureSlot, height = rowHeight)
            .clip(shape)
            .pressable(
                interactionSource = interactionSource,
                shape = shape,
                onClickLabel = label,
                onClick = onClick,
            )
            .semantics { contentDescription = label },
    ) {
        DisclosureChevron(
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(10.dp)
                .graphicsLayer { rotationZ = rotation },
        )
    }
}
