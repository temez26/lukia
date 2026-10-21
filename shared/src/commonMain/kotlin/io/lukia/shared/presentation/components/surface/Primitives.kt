package io.lukia.shared.presentation.components.surface

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.shared.presentation.theme.LukiaAlpha
import io.lukia.shared.presentation.theme.Space
import io.lukia.shared.presentation.theme.StateMeasure

/**
 * The app's one hairline.
 *
 * There were four of these - between menu rows, between detail rows, between the
 * groups in the reader's settings panel, above its contents list - drawn at the
 * same weight and colour by four separate private helpers, which is three chances
 * for one of them to drift.
 *
 * [inset] is the gap left at the start edge, in the way iOS aligns a separator to
 * the text rather than to the container. A divider between whole sections rather
 * than between rows passes it on both sides instead.
 */
@Composable
fun LukiaDivider(
    modifier: Modifier = Modifier,
    inset: Dp = 0.dp,
    endInset: Dp = 0.dp,
) {
    HorizontalDivider(
        thickness = Dp.Hairline,
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = modifier.padding(start = inset, end = endInset),
    )
}

/** What a badge is saying, which is the only thing that changes between them. */
enum class BadgeTone {
    Error,
    Neutral,

    /** Something going well, in whatever colour the app is highlighting with. */
    Accent,
}

/**
 * A short state, set in a tinted pill: a file the library can no longer find, and
 * anything else the reader has to be told about a book without leaving the card.
 *
 * One component rather than the two near-identical ones the card and the detail
 * screen each grew, which had settled on different corner radii for the same
 * sentence.
 */
@Composable
fun StatusBadge(
    text: String,
    modifier: Modifier = Modifier,
    tone: BadgeTone = BadgeTone.Error,
) {
    val color = when (tone) {
        BadgeTone.Error -> MaterialTheme.colorScheme.error
        BadgeTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
        BadgeTone.Accent -> MaterialTheme.colorScheme.primary
    }

    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(color.copy(alpha = LukiaAlpha.Badge))
            .padding(horizontal = Space.md, vertical = Space.xxs),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

/**
 * A grouped list of rows on a raised surface, in the style of an iOS settings
 * card: rounded, unshadowed, separated from its neighbours by colour rather than
 * by depth.
 *
 * [color] is a parameter because what this has to contrast with differs. The
 * detail and settings screens lay it on the window background; the overlay panels
 * lay it on a raised surface, and in the dark theme the background is true black,
 * so no single colour is a step away from both.
 */
@Composable
fun GroupCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = color,
        content = content,
    )
}

/**
 * The quiet label over a group of controls or rows.
 *
 * The reader's settings panel, its contents panel and the settings screen each
 * had their own version of this, at two different type sizes and two different
 * weights, for a label doing exactly the same job in all three.
 */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
            alpha = if (enabled) 1f else LukiaAlpha.Disabled,
        ),
        modifier = modifier,
    )
}

/**
 * One group of a settings-style page: a quiet header above a rounded surface,
 * with any explanation set below it rather than inside.
 *
 * The shape iOS gives a grouped list, and what both the settings page and the
 * statistics are built from - hence here rather than inside either.
 *
 * [color] is passed on to the [GroupCard]: what a group has to contrast with
 * differs between a screen on the window background and a panel on a raised
 * surface.
 */
@Composable
fun GroupSection(
    /** Null leaves the header off, for a group the page's own title already names. */
    title: String?,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    footnote: String? = null,
    /** Rows that draw their own vertical padding ask for none of the card's. */
    padding: Dp = Space.lg,
    /** Rows that run to the card's edges, pressed highlight and all, ask for none. */
    horizontalPadding: Dp = Space.lg,
    /**
     * The mark that opens whatever this group needs a paragraph to say - see
     * `InfoButton`. A slot rather than a string, so this primitive stays a
     * layout and does not have to know what a popover is.
     */
    info: (@Composable () -> Unit)? = null,
    /**
     * A mark on the group's name, for a group that is not what the rest of the
     * page is - see the beta badge in the settings screens. Between the name and
     * [info] because it qualifies the name, and the mark that explains the group
     * comes after both.
     */
    badge: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = Space.xs, bottom = Space.sm),
            ) {
                SectionLabel(text = title)

                badge?.invoke()

                // Beside the name rather than at the far edge of the row: it is
                // about this group, and a mark floating in white space next to a
                // short title is about nothing in particular.
                info?.invoke()
            }
        }

        GroupCard(color = color) {
            Box(modifier = Modifier.padding(horizontal = horizontalPadding, vertical = padding)) {
                content()
            }
        }

        if (footnote != null) {
            Text(
                text = footnote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Space.xs, top = Space.sm),
            )
        }
    }
}

/**
 * A screen, or a region of one, with nothing in it - and the reason why.
 *
 * Distinct from [ErrorState], which is for something that was asked for and could
 * not be shown: this is for something that simply is not there yet. [content] is
 * for whatever the reader should press to change that.
 */
@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    maxWidth: Dp = StateMeasure,
    content: (@Composable () -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.widthIn(max = maxWidth),
    ) {
        if (content != null) {
            content()
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = if (content != null) Space.xxl else 0.dp),
        )

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Space.sm),
        )
    }
}
