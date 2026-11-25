package io.lukia.features.stats.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.core.infrastructure.MonthForm
import io.lukia.core.infrastructure.monthName
import io.lukia.features.stats.domain.PeriodKind
import io.lukia.features.stats.domain.PeriodReading
import io.lukia.features.stats.presentation.changeLine
import io.lukia.features.stats.presentation.components.textSized
import io.lukia.features.stats.presentation.readingTimeOrDash
import io.lukia.features.stats.presentation.streakLine
import io.lukia.resources.Res
import io.lukia.resources.stats_best_day
import io.lukia.resources.stats_best_month
import io.lukia.resources.stats_daily_average
import io.lukia.resources.stats_days_of
import io.lukia.resources.stats_days_read
import io.lukia.resources.stats_figure_and_label
import io.lukia.resources.stats_in_sittings
import io.lukia.resources.stats_longest_run
import io.lukia.resources.stats_time_dash
import io.lukia.resources.stats_time_read
import io.lukia.shared.presentation.components.icons.CalendarGlyph
import io.lukia.shared.presentation.components.icons.ChartGlyph
import io.lukia.shared.presentation.components.icons.ClockGlyph
import io.lukia.shared.presentation.components.icons.FlameGlyph
import io.lukia.shared.presentation.components.icons.PeakGlyph
import io.lukia.shared.presentation.components.icons.SparkleGlyph
import io.lukia.shared.presentation.theme.LukiaAlpha
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private val TileGlyph: Dp = 18.dp

/** Tiles to a row: two, so a label as long as "Gelesene Tage" keeps its line. */
private const val TilesPerRow = 2

/** One figure of a period: what it is, drawn as an icon, its value and its name. */
@Immutable
private class Figure(
    val glyph: @Composable (Color, Modifier) -> Unit,
    val value: String,
    val label: String,
    /** A line under the value that qualifies it, as "in 312 sittings". */
    val detail: String? = null,
)

/**
 * What the period comes to: how it compares with the one before, then its
 * figures as tiles - for a month, time read, the daily average, days read and
 * the longest run; for a year the same, with how many sittings the time took,
 * the best month and the best day besides.
 *
 * Tiles rather than a row of numbers, each with its own icon, so the column
 * beside the calendar reads as a set of distinct facts at a glance. A month's
 * four come first in a year too, so zooming out adds figures rather than moving
 * the ones the reader was looking at.
 */
@Composable
internal fun PeriodFigures(
    reading: PeriodReading,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Space.md),
        modifier = modifier.fillMaxWidth(),
    ) {
        val change = changeLine(reading)

        // Held when there is nothing to compare with, so paging onto such a
        // period doesn't shorten the column.
        ChangePill(
            text = change.orEmpty(),
            rising = (reading.changePercent ?: 0) > 0,
            modifier = if (change == null) Modifier.alpha(0f).clearAndSetSemantics { } else Modifier,
        )

        figures(reading).chunked(TilesPerRow).forEach { row ->
            // As tall as its tallest tile, so a tile with a line more doesn't
            // leave its neighbour short.
            Row(
                horizontalArrangement = Arrangement.spacedBy(Space.md),
                modifier = Modifier.height(IntrinsicSize.Min),
            ) {
                row.forEach { figure ->
                    FigureTile(figure = figure, modifier = Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun figures(reading: PeriodReading): List<Figure> {
    val dash = stringResource(Res.string.stats_time_dash)
    val isYear = reading.period.kind == PeriodKind.Year
    val longestRun = Figure(
        glyph = { tint, modifier -> FlameGlyph(tint = tint, modifier = modifier) },
        value = if (reading.longestRun > 0) streakLine(reading.longestRun) else dash,
        label = stringResource(Res.string.stats_longest_run),
    )
    val month = listOf(
        Figure(
            glyph = { tint, modifier -> ClockGlyph(tint = tint, modifier = modifier) },
            value = readingTimeOrDash(reading.totalMs),
            label = stringResource(Res.string.stats_time_read),
            detail = if (isYear && reading.sittings > 0) {
                pluralStringResource(Res.plurals.stats_in_sittings, reading.sittings, reading.sittings)
            } else {
                null
            },
        ),
        Figure(
            glyph = { tint, modifier -> ChartGlyph(tint = tint, modifier = modifier) },
            value = readingTimeOrDash(reading.dailyAverageMs),
            label = stringResource(Res.string.stats_daily_average),
        ),
        Figure(
            glyph = { tint, modifier -> CalendarGlyph(tint = tint, modifier = modifier) },
            value = stringResource(Res.string.stats_days_of, reading.daysRead, reading.daysReached),
            label = stringResource(Res.string.stats_days_read),
        ),
    )

    if (!isYear) return month + longestRun

    return month + listOf(
        // By its short name, which fits half a phone at the largest type where
        // "September" does not.
        Figure(
            glyph = { tint, modifier -> SparkleGlyph(tint = tint, modifier = modifier) },
            value = reading.busiestMonth?.let { monthName(it.month, MonthForm.Short) } ?: dash,
            label = stringResource(Res.string.stats_best_month),
        ),
        Figure(
            glyph = { tint, modifier -> PeakGlyph(tint = tint, modifier = modifier) },
            value = readingTimeOrDash(reading.busiestDayMs),
            label = stringResource(Res.string.stats_best_day),
        ),
        longestRun,
    )
}

/** One figure on a quiet tile, announced as one phrase. */
@Composable
private fun FigureTile(
    figure: Figure,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val value = listOfNotNull(figure.value, figure.detail).joinToString(" ")
    val spoken = stringResource(Res.string.stats_figure_and_label, value, figure.label)

    Column(
        verticalArrangement = Arrangement.spacedBy(Space.xs),
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(colors.onSurfaceVariant.copy(alpha = LukiaAlpha.Hover))
            .clearAndSetSemantics { contentDescription = spoken }
            .padding(Space.md),
    ) {
        figure.glyph(colors.primary, Modifier.size(textSized(TileGlyph)))

        Text(
            text = figure.value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        figure.detail?.let { detail ->
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Two lines, for the reason `StatFigure` gives its label two.
        Text(
            text = figure.label,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * How the period compares with the one before, on a pill: the accent for a
 * rise, quiet for a fall - a slower month is not an error, so it is not red.
 */
@Composable
private fun ChangePill(text: String, rising: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val tone = if (rising) colors.primary else colors.onSurfaceVariant

    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = tone,
        modifier = modifier
            .clip(CircleShape)
            .background(tone.copy(alpha = if (rising) LukiaAlpha.Selected else LukiaAlpha.Hover))
            .padding(horizontal = Space.md, vertical = Space.xs),
    )
}
