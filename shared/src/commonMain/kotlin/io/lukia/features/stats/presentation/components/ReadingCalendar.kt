package io.lukia.features.stats.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.features.stats.domain.DayReading
import io.lukia.features.stats.domain.PeriodReading
import io.lukia.features.stats.presentation.dayLabel
import io.lukia.features.stats.presentation.dayName
import io.lukia.features.stats.presentation.readingTime
import io.lukia.resources.Res
import io.lukia.resources.stats_calendar_day_spoken
import io.lukia.resources.stats_still_to_come
import io.lukia.resources.stats_year_days_spoken
import io.lukia.shared.presentation.theme.LukiaAlpha
import io.lukia.shared.presentation.theme.Space
import kotlinx.datetime.isoDayNumber
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private const val DaysInWeek = 7

/** The most weeks a month touches, which every month is laid out as. */
private const val WeeksInCalendar = 6

/** How many shades a day's reading is drawn in, besides none. */
private const val Shades = 4

/** The faintest a shaded day is drawn, so a short day still reads as a day read. */
private const val FaintestShade = 0.25f

/** From this shade the day's number is set on the accent rather than beside it. */
private const val ShadeForOnPrimaryText = 3

private val TodayRing: Dp = 1.5.dp

private val GridGap: Dp = 1.5.dp

/**
 * How dark a day is drawn: none for a day without reading, then one to [Shades]
 * by its share of the busiest day shown.
 *
 * Relative to the period rather than to fixed minutes, as the bar chart is: a
 * reader of twenty minutes a day should see their own good days stand out.
 */
internal fun shadeOf(durationMs: Long, busiestMs: Long): Int = when {
    durationMs <= 0 || busiestMs <= 0 -> 0
    else -> ((durationMs.toFloat() / busiestMs) * Shades).toInt().coerceIn(1, Shades)
}

private fun shadeAlpha(shade: Int): Float =
    FaintestShade + (1f - FaintestShade) * (shade - 1) / (Shades - 1)

/**
 * A month as a calendar, Monday first, each day shaded by how much was read on
 * it.
 *
 * A calendar rather than thirty bars because the question a month answers is
 * which days, and runs and gaps are what a calendar shows at a glance.
 *
 * Always six weeks tall, as most calendars are, so paging from a month of four
 * weeks to one of six doesn't move the cards below.
 */
@Composable
internal fun MonthCalendar(
    month: PeriodReading,
    modifier: Modifier = Modifier,
) {
    val busiest = month.busiestDayMs
    val lead = month.days.first().date.dayOfWeek.isoDayNumber - 1
    val trail = WeeksInCalendar * DaysInWeek - lead - month.days.size
    val cells: List<DayReading?> = List(lead) { null } + month.days + List(trail) { null }
    val weeks = cells.chunked(DaysInWeek)

    Column(
        verticalArrangement = Arrangement.spacedBy(Space.xs),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            month.days.take(DaysInWeek).sortedBy { it.date.dayOfWeek.isoDayNumber }.forEach { day ->
                Text(
                    text = dayLabel(day.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.weight(1f).clearAndSetSemantics { },
                    textAlign = TextAlign.Center,
                )
            }
        }

        weeks.forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                for (day in week) {
                    if (day == null) {
                        // Square like a day, so a week of none still holds its row.
                        Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        CalendarDay(day = day, busiestMs = busiest, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDay(
    day: DayReading,
    busiestMs: Long,
    modifier: Modifier = Modifier,
) {
    val shade = if (day.isFuture) 0 else shadeOf(day.durationMs, busiestMs)
    val shape = MaterialTheme.shapes.small
    val colors = MaterialTheme.colorScheme
    val spoken = stringResource(
        Res.string.stats_calendar_day_spoken,
        dayName(day.date),
        day.date.day,
        if (day.isFuture) stringResource(Res.string.stats_still_to_come) else readingTime(day.durationMs),
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(
                when {
                    day.isFuture -> Color.Transparent
                    shade == 0 -> colors.onSurfaceVariant.copy(alpha = LukiaAlpha.Hover)
                    else -> colors.primary.copy(alpha = shadeAlpha(shade))
                },
            )
            .then(if (day.isToday) Modifier.border(TodayRing, colors.onSurface, shape) else Modifier)
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        Text(
            text = day.date.day.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (day.isToday) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            color = when {
                day.isFuture -> colors.onSurfaceVariant.copy(alpha = LukiaAlpha.Disabled)
                shade >= ShadeForOnPrimaryText -> colors.onPrimary
                else -> colors.onSurface
            },
        )
    }
}

/**
 * Every day of a year as one small square, a week to a column.
 *
 * Drawn on a canvas and announced as one sentence: 365 cells are a picture of
 * the year, not 365 things to tab through.
 */
@Composable
internal fun YearGrid(
    year: PeriodReading,
    modifier: Modifier = Modifier,
) {
    val busiest = year.busiestDayMs
    val lead = year.days.first().date.dayOfWeek.isoDayNumber - 1
    val columns = (lead + year.days.size + DaysInWeek - 1) / DaysInWeek
    val colors = MaterialTheme.colorScheme
    val spoken = pluralStringResource(
        Res.plurals.stats_year_days_spoken,
        year.daysRead,
        year.daysRead,
        year.period.start.year,
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(columns.toFloat() / DaysInWeek)
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        val gap = GridGap.toPx()
        val cell = (size.width - gap * (columns - 1)) / columns
        val radius = CornerRadius(cell / 4)

        year.days.forEachIndexed { index, day ->
            val slot = lead + index
            val shade = if (day.isFuture) 0 else shadeOf(day.durationMs, busiest)
            val color = when {
                day.isFuture -> colors.onSurfaceVariant.copy(alpha = LukiaAlpha.Hover / 2)
                shade == 0 -> colors.onSurfaceVariant.copy(alpha = LukiaAlpha.Hover)
                else -> colors.primary.copy(alpha = shadeAlpha(shade))
            }

            drawRoundRect(
                color = color,
                topLeft = Offset((slot / DaysInWeek) * (cell + gap), (slot % DaysInWeek) * (cell + gap)),
                size = Size(cell, cell),
                cornerRadius = radius,
            )
        }
    }
}
