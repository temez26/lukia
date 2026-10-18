package io.lukia.features.reader.presentation.components.contents

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.features.reader.domain.outline.OutlineEntry
import io.lukia.features.reader.presentation.ChapterRow
import io.lukia.features.reader.presentation.ChapterStanding
import io.lukia.resources.Res
import io.lukia.resources.book_length_pages
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
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Indent per level of nesting. */
private val LevelIndent: Dp = 20.dp

/** See `chevronLift` in [ChapterRowItem]. */
private val ChevronLift: Dp = 5.dp

/** How far into a level's indent its guide line sits. */
private val GuideOffset: Dp = 4.dp

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

/** The bar under the title of the row the reader is in. */
private val ChapterProgressHeight: Dp = 3.dp

// One row of the table of contents: its indent, its title, its page number and
// its fold control. The widest part of the list, and the only part that draws
// anything itself.

/**
 * One entry in the contents.
 *
 * Laid out as Kindle and Play Books lay theirs: the title starts at the panel's
 * own edge, in line with the book and the scrubber above it, and the fold
 * control sits at the end of the row. With the control at the start, every
 * title began a thumb's width in, behind a column that most rows left empty.
 *
 * Chapter against section is carried three ways over, because indentation alone
 * is not legible in a panel this narrow: a chapter is set in a heavier, larger
 * face; every level of nesting adds a hairline guide down the start edge; and a
 * hairline above each chapter closes off the one before it with its sections.
 */
@Composable
internal fun ChapterRowItem(
    row: ChapterRow,
    inset: Dp,
    /**
     * Whether anything in the contents folds. A flat list - most books - has no
     * control to line titles up behind, so it keeps no empty column for one.
     */
    foldable: Boolean,
    standing: ChapterStanding,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    /** Whether a hairline closes off what came before: every chapter but the first. */
    divided: Boolean = false,
) {
    val isCurrent = standing.isCurrent
    val entry = row.entry
    val isChapter = entry.level == 0
    val metrics = LocalLukiaMetrics.current
    val interactionSource = remember { MutableInteractionSource() }
    // The chevron's middle sits this far above the title's baseline: about the
    // middle of a lowercase letter, so it reads as level with the first line.
    val chevronLift = with(LocalDensity.current) { ChevronLift.roundToPx() }

    // Read before the modifier chain: `semantics` is not a composable scope.
    val goTo = stringResource(Res.string.reader_go_to_chapter, entry.title)
    // A chapter not laid out yet has no page to name, only a title.
    val spoken = if (entry.placed) {
        stringResource(Res.string.reader_chapter_at_page, entry.title, entry.pageIndex + 1)
    } else {
        entry.title
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .rowMarks(
                level = entry.level,
                inset = inset,
                isCurrent = isCurrent,
                divided = divided,
                colors = RowMarkColors(
                    selected = MaterialTheme.colorScheme.primary.copy(alpha = LukiaAlpha.Selected),
                    line = MaterialTheme.colorScheme.outlineVariant,
                ),
            )
            .pressable(
                interactionSource = interactionSource,
                shape = RectangleShape,
                onClickLabel = goTo,
                onClick = onClick,
            )
            .heightIn(min = metrics.rowHeight)
            // Less at the end when a fold control is there: its own slot is the
            // margin, and the chevron lines up under the panel's other ends.
            .padding(start = inset + LevelIndent * entry.level, end = if (foldable) inset - Space.lg else inset)
            .semantics { contentDescription = spoken },
    ) {
        // Number and title on one baseline: centred, the number floated between
        // the two lines of a long title and belonged to neither.
        ChapterTitle(
            title = entry.title,
            isChapter = isChapter,
            standing = standing,
            modifier = Modifier
                .weight(1f)
                .alignByBaseline()
                .padding(vertical = if (isChapter) Space.lg else Space.md),
        )

        PageNumber(entry = entry, isCurrent = isCurrent, modifier = Modifier.alignByBaseline())

        if (row.hasChildren) {
            DisclosureToggle(
                collapsed = row.isCollapsed,
                title = entry.title,
                rowHeight = metrics.rowHeight,
                onClick = onToggle,
                modifier = Modifier.alignBy { it.measuredHeight / 2 + chevronLift },
            )
        } else if (foldable) {
            // Kept empty rather than closed up, so the page numbers line up down
            // the panel whether or not their row has anything to fold.
            Box(modifier = Modifier.width(DisclosureSlot))
        }
    }
}

private class RowMarkColors(val selected: Color, val line: Color)

/** Where the entry starts, in a column of numbers that lines up down the list. */
@Composable
private fun PageNumber(entry: OutlineEntry, isCurrent: Boolean, modifier: Modifier = Modifier) {
    Text(
        // Blank rather than the last page laid out so far, which is what the
        // number would be - see [OutlineEntry.placed]. The column keeps its
        // width so titles do not shift when the numbers arrive.
        text = if (entry.placed) "${entry.pageIndex + 1}" else "",
        style = MaterialTheme.typography.labelSmall,
        color = if (isCurrent) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        textAlign = TextAlign.End,
        maxLines = 1,
        modifier = modifier
            .padding(start = Space.md)
            .widthIn(min = PageNumberWidth),
    )
}

/**
 * The current row's tint; a hairline above a chapter, closing off the one before
 * it; and one guide per level of nesting, so a sub-section reads as hanging off
 * its section as well as off the chapter above them both.
 *
 * The tint alone marks the current row, with its accent title and progress bar.
 * A marker down the start edge as well was a fourth way of saying the same thing.
 */
private fun Modifier.rowMarks(
    level: Int,
    inset: Dp,
    isCurrent: Boolean,
    divided: Boolean,
    colors: RowMarkColors,
): Modifier = background(if (isCurrent) colors.selected else Color.Transparent)
    .drawBehind {
        if (divided) {
            drawLine(
                color = colors.line,
                start = Offset(inset.toPx(), 0f),
                end = Offset(size.width - inset.toPx(), 0f),
                strokeWidth = HairlineWidth.toPx(),
            )
        }

        repeat(level) { step ->
            val x = (inset + LevelIndent * step + GuideOffset).toPx()

            drawLine(
                color = colors.line,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = HairlineWidth.toPx(),
            )
        }
    }

/**
 * A row's title, with what a reader glances at a contents list for under it: how
 * long a chapter is, and how far into the one they are in they have got.
 *
 * A chapter already read is set in the muted colour, as Kobo and Kindle do, so
 * where the reader has been and where they have not reads at a glance.
 */
@Composable
private fun ChapterTitle(
    title: String,
    isChapter: Boolean,
    standing: ChapterStanding,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
        Text(
            text = title,
            style = if (isChapter) {
                MaterialTheme.typography.bodyMedium
            } else {
                MaterialTheme.typography.bodySmall
            },
            fontWeight = if (isChapter) FontWeight.SemiBold else FontWeight.Normal,
            // Muted for read and nothing else, at every level: a section set in
            // the muted colour whether read or not left "read" meaning nothing
            // under a chapter.
            color = when {
                standing.isCurrent -> MaterialTheme.colorScheme.primary
                standing.isRead -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurface
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        // Chapters only: under every section as well it is a second list of
        // numbers interleaved with the first.
        val length = standing.length
        if (isChapter && length != null) {
            Text(
                text = pluralStringResource(Res.plurals.book_length_pages, length, length),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }

        standing.progress?.let { fraction -> ChapterProgress(fraction) }
    }
}

/** How far through the row the reader is in, as a thin bar under its title. */
@Composable
private fun ChapterProgress(fraction: Float) {
    val track = MaterialTheme.colorScheme.outlineVariant
    val fill = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .padding(top = Space.xxs)
            .fillMaxWidth()
            .height(ChapterProgressHeight)
            .drawBehind {
                val radius = CornerRadius(size.height / 2)
                drawRoundRect(color = track, cornerRadius = radius)
                drawRoundRect(
                    color = fill,
                    size = Size(size.width * fraction.coerceIn(0f, 1f), size.height),
                    cornerRadius = radius,
                )
            },
    )
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
    modifier: Modifier = Modifier,
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
        modifier = modifier
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
