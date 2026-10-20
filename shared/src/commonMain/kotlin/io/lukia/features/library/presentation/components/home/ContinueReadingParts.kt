package io.lukia.features.library.presentation.components.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.domain.reading.ReadingProgress
import io.lukia.core.presentation.displayType
import io.lukia.core.presentation.formattedSize
import io.lukia.core.presentation.languageCode
import io.lukia.core.presentation.languageName
import io.lukia.core.presentation.publishedYear
import io.lukia.resources.Res
import io.lukia.resources.book_fact_spoken
import io.lukia.resources.book_format
import io.lukia.resources.book_language
import io.lukia.resources.book_progress_page_of
import io.lukia.resources.book_published
import io.lukia.resources.book_size
import io.lukia.resources.home_page_position
import io.lukia.resources.home_pages_left
import io.lukia.resources.reader_percent
import io.lukia.shared.presentation.components.controls.ProgressRing
import io.lukia.shared.presentation.components.icons.CalendarGlyph
import io.lukia.shared.presentation.components.icons.FileGlyph
import io.lukia.shared.presentation.components.icons.GlobeGlyph
import io.lukia.shared.presentation.components.icons.StorageGlyph
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** A fact's glyph, in sp so it grows with the words beside it. */
private val FactGlyphSize: TextUnit = 15.sp

/** The ring beside the cover: about as big as the read button on the other side of it. */
private val RingSize: TextUnit = 64.sp

/** Joins what is read out for the progress, as a sentence is joined. */
private const val SpokenSeparator = ", "

/**
 * How far through the book is: the ring with its percentage, and the page as
 * "126/300" under it - the column on the cover's left.
 *
 * Read out as one sentence - "42%, page 126 of 300, 174 pages left" - rather
 * than as a ring and a fraction a screen reader would announce apart. A book
 * whose length is unknown has nothing to draw here.
 */
@Composable
internal fun ContinueProgress(progress: ReadingProgress, modifier: Modifier = Modifier) {
    val fraction = progress.fraction ?: return
    val percent = progress.percent?.let { stringResource(Res.string.reader_percent, it) }.orEmpty()
    val page = progress.pageNumber
    val left = page?.let { progress.pageCount - it }?.takeIf { it > 0 }
    val spoken = listOfNotNull(
        percent,
        page?.let { stringResource(Res.string.book_progress_page_of, it, progress.pageCount) },
        left?.let { pluralStringResource(Res.plurals.home_pages_left, it, it) },
    ).joinToString(SpokenSeparator)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.sm),
        modifier = modifier.clearAndSetSemantics { contentDescription = spoken },
    ) {
        ProgressRing(
            fraction = fraction,
            label = percent,
            size = RingSize,
            labelStyle = MaterialTheme.typography.titleMedium,
        )

        if (page != null) {
            Text(
                text = stringResource(Res.string.home_page_position, page, progress.pageCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/**
 * What the file is, as a row of small facts under a hairline: its format, its
 * size, its language and the year it came out, each behind a glyph.
 *
 * A fact the book does not state is left out and the rest close up. The
 * language shows as its two-letter code where a word would not fit, and is
 * read out by name.
 */
@Composable
internal fun ContinueFacts(document: DocumentInfo, modifier: Modifier = Modifier) {
    val metadata = document.metadata
    val tint = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Spread across the card's width; at a large font size the facts that
        // no longer fit go onto a second line rather than being cut off.
        FlowRow(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(Space.sm),
            itemVerticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Space.md),
        ) {
            Fact(
                text = document.displayType(),
                spoken = spokenFact(Res.string.book_format, document.displayType()),
            ) { FileGlyph(tint = tint, modifier = it) }

            Fact(
                text = document.formattedSize(),
                spoken = spokenFact(Res.string.book_size, document.formattedSize()),
            ) { StorageGlyph(tint = tint, modifier = it) }

            metadata?.languageCode()?.let { code ->
                Fact(
                    text = code,
                    spoken = spokenFact(Res.string.book_language, metadata.languageName() ?: code),
                ) { GlobeGlyph(tint = tint, modifier = it) }
            }

            metadata?.publishedYear()?.let { year ->
                Fact(
                    text = year,
                    spoken = spokenFact(Res.string.book_published, year),
                ) { CalendarGlyph(tint = tint, modifier = it) }
            }
        }
    }
}

@Composable
private fun spokenFact(label: StringResource, value: String): String =
    stringResource(Res.string.book_fact_spoken, stringResource(label), value)

@Composable
private fun Fact(text: String, spoken: String, glyph: @Composable (Modifier) -> Unit) {
    val glyphSize = with(LocalDensity.current) { FactGlyphSize.toDp() }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
        modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
    ) {
        glyph(Modifier.size(glyphSize))

        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
