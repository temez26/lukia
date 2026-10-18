package io.lukia.features.library.presentation.screens

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
import io.lukia.features.library.application.BookDetails
import io.lukia.features.library.presentation.components.book.ReadableCover
import io.lukia.features.library.presentation.model.progressLabel
import io.lukia.features.library.presentation.model.readActionLabel
import io.lukia.resources.Res
import io.lukia.resources.book_missing_file
import io.lukia.shared.presentation.components.controls.LukiaButton
import io.lukia.shared.presentation.components.cover.FavoriteBadge
import io.lukia.shared.presentation.components.cover.rememberBookCover
import io.lukia.shared.presentation.components.icons.OpenBookGlyph
import io.lukia.shared.presentation.components.surface.StatusBadge
import io.lukia.shared.presentation.theme.Clearance
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource

// The cover, the title and what can be done with the book - everything above
// the sections that describe it.

/**
 * Everything there is to say about one book: its cover - which is also the way
 * into it - what it is called and who wrote it, what the publisher said about
 * it, and its particulars underneath.
 *
 * Shared, because this is shown two ways - a screen of its own on a phone, a
 * panel over the library on anything wider - and the two must not drift into
 * saying different things about the same book. Only the surrounding chrome and
 * the room left for it differ, which is what [topClearance] and
 * [bottomClearance] are for.
 *
 * Sections earn their place: no blurb means no About, no publisher means no
 * publisher row. A heading over an empty space only tells the reader the app
 * expected something it did not get.
 */
@Composable
internal fun BookDetailBody(
    details: BookDetails,
    onRead: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    topClearance: Dp = Space.sm,
    bottomClearance: Dp = Clearance.pageBottom,
    coverWidth: Dp = Dp.Unspecified,
    /**
     * What the grouped details sit in. It has to contrast with whatever is behind
     * this body, and that differs: the screen lays it on the window background,
     * the panel on a raised surface, and one colour cannot be a step away from
     * both - in the dark theme especially, where the background is true black.
     */
    infoCardColor: Color = MaterialTheme.colorScheme.surface,
    /** How far moving the book to or from the server has got - see [BookTransferRow]. */
    transfer: BookTransfer = BookTransfer.Idle,
    transferActions: BookTransferActions = BookTransferActions(),
    /** Whether a book on this device could be added to the server - true while signed in. */
    canUpload: Boolean = false,
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
                    cover = rememberBookCover(document.document.location, document.coverImage?.bytes),
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

            // How far in the reader already is.
            Text(
                text = details.progressLabel(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Space.sm),
            )

            // The way in, saying what it does - "Continue reading", "Start
            // reading" - as the reading apps' detail pages do. An icon on the
            // cover said "open" to nobody who had not been told.
            if (document.exists) {
                LukiaButton(
                    label = details.readActionLabel(),
                    onClick = onRead,
                    icon = { tint, glyph -> OpenBookGlyph(tint = tint, modifier = glyph) },
                    modifier = Modifier
                        .padding(top = Space.lg)
                        .widthIn(min = ReadButtonMinWidth),
                )
            }

            val direction = document.transferDirection(canUpload)
            if (direction != null) {
                BookTransferRow(
                    direction = direction,
                    state = transfer,
                    actions = transferActions,
                    modifier = Modifier.padding(top = Space.lg),
                )
            } else if (!document.exists) {
                StatusBadge(
                    text = stringResource(Res.string.book_missing_file),
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
                showServer = canUpload,
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

/** Wide enough to read as the screen's one action rather than a chip. */
private val ReadButtonMinWidth = 220.dp
