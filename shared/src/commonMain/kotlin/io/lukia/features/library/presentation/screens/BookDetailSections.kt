package io.lukia.features.library.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import io.lukia.core.presentation.displayType
import io.lukia.core.presentation.formattedSize
import io.lukia.core.presentation.languageName
import io.lukia.core.presentation.publishedYear
import io.lukia.features.library.application.BookDetails
import io.lukia.features.library.presentation.model.lengthLabel
import io.lukia.resources.Res
import io.lukia.resources.book_about
import io.lukia.resources.book_format
import io.lukia.resources.book_information
import io.lukia.resources.book_isbn
import io.lukia.resources.book_language
import io.lukia.resources.book_length
import io.lukia.resources.book_less
import io.lukia.resources.book_more
import io.lukia.resources.book_published
import io.lukia.resources.book_publisher
import io.lukia.resources.book_size
import io.lukia.resources.book_subjects
import io.lukia.shared.presentation.components.controls.ButtonSize
import io.lukia.shared.presentation.components.controls.ButtonVariant
import io.lukia.shared.presentation.components.controls.LukiaButton
import io.lukia.shared.presentation.components.controls.buttonInset
import io.lukia.shared.presentation.components.surface.BadgeTone
import io.lukia.shared.presentation.components.surface.GroupCard
import io.lukia.shared.presentation.components.surface.LukiaDivider
import io.lukia.shared.presentation.components.surface.SectionLabel
import io.lukia.shared.presentation.components.surface.StatusBadge
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource

/** How much of a blurb is shown before it is folded away behind "More". */
private const val CollapsedDescriptionLines = 5

/**
 * Inset from the card's edge to a row's text, and what the separators between
 * rows are inset by so they align to the text rather than to the container.
 */
private val InfoRowInset: Dp = Space.lg

// What is written about the book rather than what can be done with it: the
// blurb, the file and format details, and its subjects.

/**
 * The publisher's blurb, folded to a few lines with a way to see the rest.
 *
 * The fold is offered only when there is something folded away. Whether the text
 * overflows is something only the layout knows, so it is asked rather than
 * guessed at from a character count - which would put a "More" under a blurb
 * that was already showing in full whenever the window was wide.
 */
@Composable
internal fun DescriptionSection(
    description: String,
    modifier: Modifier = Modifier,
) {
    var expanded by remember(description) { mutableStateOf(false) }
    var overflows by remember(description) { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        SectionLabel(text = stringResource(Res.string.book_about))

        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            // The blurb is the one paragraph on this screen anyone reads at
            // length, so it gets full-strength body colour rather than the muted
            // one the particulars beneath it wear.
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = if (expanded) Int.MAX_VALUE else CollapsedDescriptionLines,
            overflow = TextOverflow.Ellipsis,
            // Only meaningful while folded: expanded text never overflows, and
            // recording that would take the control away mid-read.
            onTextLayout = { layout ->
                if (!expanded) overflows = layout.hasVisualOverflow
            },
            modifier = Modifier.padding(top = Space.sm),
        )

        if (overflows || expanded) {
            LukiaButton(
                label = stringResource(
                    if (expanded) Res.string.book_less else Res.string.book_more,
                ),
                onClick = { expanded = !expanded },
                variant = ButtonVariant.Ghost,
                size = ButtonSize.Compact,
                // Pulled back by its own inset so the label starts on the same
                // line as the paragraph it belongs to; a ghost button has no
                // container to make that padding look deliberate.
                modifier = Modifier.offset(x = -buttonInset(ButtonSize.Compact)),
            )
        }
    }
}

/**
 * The book's particulars, in the style of an iOS grouped list.
 *
 * Built from whatever there is rather than from a fixed set of rows, so a book
 * that named a publisher gets a publisher row and one that did not is not given
 * an empty one. Where the file lives is kept out of the group and set below it:
 * it is the only value long enough to need two lines, and the only one that is
 * about the file rather than about the book.
 */
@Composable
internal fun InformationCard(
    details: BookDetails,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val document = details.document
    val metadata = document.metadata

    val rows = buildList {
        metadata?.publisher?.let { add(stringResource(Res.string.book_publisher) to it) }
        metadata?.publishedYear()?.let { add(stringResource(Res.string.book_published) to it) }
        metadata?.languageName()?.let { add(stringResource(Res.string.book_language) to it) }
        details.lengthLabel()?.let { add(stringResource(Res.string.book_length) to it) }
        add(stringResource(Res.string.book_format) to document.displayType())
        add(stringResource(Res.string.book_size) to document.formattedSize())
        metadata?.identifier?.let { add(stringResource(Res.string.book_isbn) to it) }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        SectionLabel(text = stringResource(Res.string.book_information))

        GroupCard(
            color = color,
            modifier = Modifier.padding(top = Space.sm),
        ) {
            Column {
                rows.forEachIndexed { index, (label, value) ->
                    if (index > 0) LukiaDivider(inset = InfoRowInset)

                    InfoRow(label = label, value = value)
                }
            }
        }

        Text(
            text = document.document.location,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Space.xs, top = Space.sm),
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = InfoRowInset, vertical = Space.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            textAlign = TextAlign.End,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = InfoRowInset),
        )
    }
}

/**
 * What the book filed itself under, as tags.
 *
 * Wrapped rather than scrolled sideways: a book can carry a dozen BISAC headings
 * and a row that ran off the edge would hide most of them behind a gesture
 * nothing on screen suggests.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SubjectSection(
    subjects: List<String>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionLabel(text = stringResource(Res.string.book_subjects))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
            modifier = Modifier.padding(top = Space.sm),
        ) {
            subjects.forEach { subject ->
                StatusBadge(text = subject, tone = BadgeTone.Neutral)
            }
        }
    }
}
