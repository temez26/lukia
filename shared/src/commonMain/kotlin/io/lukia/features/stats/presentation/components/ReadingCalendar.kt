package io.lukia.features.stats.presentation.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import io.lukia.features.stats.domain.DayReading
import io.lukia.features.stats.domain.PeriodReading
import io.lukia.features.stats.presentation.dayLabel
import io.lukia.features.stats.presentation.dayName
import io.lukia.features.stats.presentation.readingTime
import io.lukia.resources.Res
import io.lukia.resources.stats_calendar_day_spoken
import io.lukia.resources.stats_still_to_come
import io.lukia.shared.presentation.theme.LocalMotionStyle
import io.lukia.shared.presentation.theme.LukiaAlpha
import io.lukia.shared.presentation.theme.Space
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import org.jetbrains.compose.resources.stringResource

private const val DaysInWeek = 7

/** How many shades a day's reading is drawn in, besides none. */
private const val Shades = 4

/** The faintest a shaded day is drawn, so a short day still reads as a day read. */
private const val FaintestShade = 0.25f

/** From this shade the day's number is set on the accent rather than beside it. */
private const val ShadeForOnPrimaryText = 3

/**
 * How much of its colour a calendar day keeps beside the selected one: more than
 * a bar does, because here the shades are the picture.
 */
private const val CalendarUnselectedShare = 0.75f

/**
 * The tallest a calendar day is drawn, however wide the card. A day as tall as
 * it is wide made a month on a wide window taller than everything beside it.
 */
private val MaxCellHeight: Dp = 40.dp

private val TodayRing: Dp = 1.5.dp

private val SelectedRing: Dp = 2.dp

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

internal fun shadeAlpha(shade: Int): Float =
    FaintestShade + (1f - FaintestShade) * (shade - 1) / (Shades - 1)

/**
 * A month as a calendar, Monday first, each day shaded by how much was read on
 * it, and one of them selected.
 *
 * A calendar rather than thirty bars because the question a month answers is
 * which days, and runs and gaps are what a calendar shows at a glance.
 *
 * Only as many weeks tall as the month touches, its days capped at
 * [MaxCellHeight]: the card is as tall as what it shows - see
 * `ReadingTimeSection` - and grows or shrinks smoothly when the next month
 * needs a row more or less.
 */
@Composable
internal fun MonthCalendar(
    month: PeriodReading,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = LocalMotionStyle.current
    val busiest = month.busiestDayMs
    val lead = month.days.first().date.dayOfWeek.isoDayNumber - 1
    val weeks = (List(lead) { null } + month.days).chunked(DaysInWeek)
    val select = daySelector(selected, onSelect)
    val slot = lead + month.days.indexOfFirst { it.date == selected }.coerceAtLeast(0)
    val column by animateFloatAsState((slot % DaysInWeek).toFloat(), motion.settle(), label = "ringColumn")
    val row by animateFloatAsState((slot / DaysInWeek).toFloat(), motion.settle(), label = "ringRow")

    Column(
        verticalArrangement = Arrangement.spacedBy(Space.xs),
        modifier = modifier.fillMaxWidth(),
    ) {
        WeekdayHeader(days = month.days)

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val gap = Space.xs
            val cellHeight = minOf((maxWidth - gap * (DaysInWeek - 1)) / DaysInWeek, MaxCellHeight)

            Column(
                verticalArrangement = Arrangement.spacedBy(gap),
                modifier = Modifier
                    .animateContentSize(motion.settle())
                    .selectionRing(
                        slot = Offset(column, row),
                        rows = weeks.size,
                        gap = gap,
                        color = MaterialTheme.colorScheme.primary,
                        shape = MaterialTheme.shapes.small,
                    ),
            ) {
                weeks.forEach { week ->
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        for (index in 0 until DaysInWeek) {
                            val day = week.getOrNull(index)
                            if (day == null) {
                                Box(modifier = Modifier.weight(1f).heightIn(min = cellHeight))
                            } else {
                                CalendarDay(
                                    day = day,
                                    busiestMs = busiest,
                                    isSelected = day.date == selected,
                                    onClick = { select(day.date) },
                                    modifier = Modifier.weight(1f).heightIn(min = cellHeight),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The weekday names over a calendar's columns, Monday first. */
@Composable
private fun WeekdayHeader(days: List<DayReading>, modifier: Modifier = Modifier) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.xs), modifier = modifier) {
        days.take(DaysInWeek).sortedBy { it.date.dayOfWeek.isoDayNumber }.forEach { day ->
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
}

/**
 * One ring for the whole month, drawn over the day in column and row [slot] -
 * fractional while it slides to the selected day rather than jumping there.
 */
private fun Modifier.selectionRing(
    slot: Offset,
    rows: Int,
    gap: Dp,
    color: Color,
    shape: Shape,
): Modifier = drawWithContent {
    drawContent()
    val gapPx = gap.toPx()
    val cell = Size(
        (size.width - gapPx * (DaysInWeek - 1)) / DaysInWeek,
        (size.height - gapPx * (rows - 1)) / rows,
    )
    val outline = shape.createOutline(cell, layoutDirection, this)
    translate(slot.x * (cell.width + gapPx), slot.y * (cell.height + gapPx)) {
        drawOutline(outline, color, style = Stroke(SelectedRing.toPx()))
    }
}

@Composable
private fun CalendarDay(
    day: DayReading,
    busiestMs: Long,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = LocalMotionStyle.current
    val shade = if (day.isFuture) 0 else shadeOf(day.durationMs, busiestMs)
    val shape = MaterialTheme.shapes.small
    val colors = MaterialTheme.colorScheme
    val emphasis by animateFloatAsState(
        targetValue = if (isSelected) 1f else CalendarUnselectedShare,
        animationSpec = motion.settle(),
        label = "dayEmphasis",
    )
    val spoken = stringResource(
        Res.string.stats_calendar_day_spoken,
        dayName(day.date),
        day.date.day,
        if (day.isFuture) stringResource(Res.string.stats_still_to_come) else readingTime(day.durationMs),
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .graphicsLayer { alpha = emphasis }
            .clip(shape)
            .background(
                when {
                    day.isFuture -> Color.Transparent
                    shade == 0 -> colors.onSurfaceVariant.copy(alpha = LukiaAlpha.Hover)
                    else -> colors.primary.copy(alpha = shadeAlpha(shade))
                },
            )
            .then(if (day.isToday) Modifier.border(TodayRing, colors.onSurface, shape) else Modifier)
            .selectable(selected = isSelected, onClick = onClick, indication = null, interactionSource = null)
            .clearAndSetSemantics {
                contentDescription = spoken
                selected = isSelected
                role = Role.Button
                onClick {
                    onClick()
                    true
                }
            },
    ) {
        Text(
            text = day.date.day.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (day.isToday || isSelected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            color = when {
                day.isFuture -> colors.onSurfaceVariant.copy(alpha = LukiaAlpha.Disabled)
                shade >= ShadeForOnPrimaryText -> colors.onPrimary
                else -> colors.onSurface
            },
            modifier = Modifier.padding(vertical = Space.xxs),
        )
    }
}
