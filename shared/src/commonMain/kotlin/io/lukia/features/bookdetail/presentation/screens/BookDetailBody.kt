package io.lukia.features.bookdetail.presentation.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.core.presentation.authorLine
import io.lukia.core.presentation.bookTitle
import io.lukia.features.bookdetail.application.BookDetails
import io.lukia.features.bookdetail.presentation.components.ReadableCover
import io.lukia.features.bookdetail.presentation.progressLabel
import io.lukia.features.bookdetail.presentation.readActionLabel
import io.lukia.shared.presentation.components.FavoriteBadge
import io.lukia.shared.presentation.components.StatusBadge
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.Space

/** Vertical room reserved so content starts clear of the floating back control. */
private val TopChromeClearance: Dp = 68.dp

/** Room past the last row, so the details do not end hard against the edge. */
private val DetailBottomClearance: Dp = 32.dp

// The cover, the title and what can be done with the book - everything above
// the sections that describe it.

/**
 * Everything there is to say about one book: its cover - which is also the way
 * into it - what it is called and who wrote it, what the publisher said about
 * it, and its particulars underneath.
 *
 * Shared, because this is shown two ways - as a screen of its own on a phone, and
 * as a panel over the library on anything wider - and the two must not drift into
 * saying different things about the same book. Only the chrome around it and the
 * room it leaves for that chrome differ, which is what [topClearance] and
 * [bottomClearance] are for.
 *
 * Sections earn their place: a book whose file carries no blurb has no About, and
 * one that named no publisher has no publisher row. A heading over an empty space
 * tells the reader only that the app expected something it did not get.
 */
@Composable
internal fun BookDetailBody(
    details: BookDetails,
    onRead: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    topClearance: Dp = TopChromeClearance,
    bottomClearance: Dp = DetailBottomClearance,
    coverWidth: Dp = Dp.Unspecified,
    /**
     * What the grouped details sit in. It has to contrast with whatever is behind
     * this body, and that differs: the screen lays it on the window background,
     * the panel on a raised surface, and one colour cannot be a step away from
     * both - in the dark theme especially, where the background is true black.
     */
    infoCardColor: Color = MaterialTheme.colorScheme.surface,
) {
    val dimens = LocalLukiaDimens.current
    val document = details.document
    val metadata = document.metadata
    val title = document.bookTitle()

    // A screen can give the cover its full size, because the screen is the book.
    // A panel cannot: the cover would push everything worth reading below the
    // fold in any window that is not tall.
    val resolvedCoverWidth =
        if (coverWidth == Dp.Unspecified) dimens.detailCoverWidth else coverWidth

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.verticalScroll(rememberScrollState()),
    ) {
        Spacer(modifier = Modifier.height(topClearance))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(max = dimens.maxDetailWidth)
                .fillMaxWidth()
                .padding(horizontal = dimens.screenPadding),
        ) {
            Box {
                ReadableCover(
                    coverImage = document.coverImage,
                    title = title,
                    progress = details.progress,
                    enabled = document.exists,
                    onRead = onRead,
                    onLongPress = onLongPress,
                    width = resolvedCoverWidth,
                )

                FavoriteBadge(
                    visible = document.isFavorite,
                    size = 28.dp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(Space.md),
                )
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = dimens.sectionSpacing),
            )

            // The accent colour, as a book's byline is set everywhere it is set:
            // it is the second thing anyone reads and the one that decides
            // whether this is the book they meant.
            metadata?.authorLine()?.let { authors ->
                Text(
                    text = authors,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Space.xs),
                )
            }

            // What pressing the cover would do, and how far in the reader
            // already is - the caption the cover cannot carry itself.
            Text(
                text = if (document.exists) {
                    "${details.readActionLabel()} · ${details.progressLabel()}"
                } else {
                    details.progressLabel()
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Space.sm),
            )

            if (!document.exists) {
                StatusBadge(
                    text = "This file is no longer available",
                    modifier = Modifier.padding(top = Space.lg),
                )
            }

            metadata?.description?.let { description ->
                DescriptionSection(
                    description = description,
                    modifier = Modifier.padding(top = dimens.sectionSpacing),
                )
            }

            InformationCard(
                details = details,
                color = infoCardColor,
                modifier = Modifier.padding(top = dimens.sectionSpacing),
            )

            metadata?.subjects?.takeIf { it.isNotEmpty() }?.let { subjects ->
                SubjectSection(
                    subjects = subjects,
                    modifier = Modifier.padding(top = dimens.sectionSpacing),
                )
            }
        }

        Spacer(modifier = Modifier.height(bottomClearance))
    }
}
