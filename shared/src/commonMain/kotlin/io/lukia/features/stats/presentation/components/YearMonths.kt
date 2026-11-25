package io.lukia.features.stats.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.core.infrastructure.MonthForm
import io.lukia.core.infrastructure.monthName
import io.lukia.features.stats.domain.DayReading
import io.lukia.features.stats.domain.PeriodReading
import io.lukia.resources.Res
import io.lukia.resources.stats_show_month
import io.lukia.resources.stats_year_days_spoken
import io.lukia.shared.presentation.components.controls.pressable
import io.lukia.shared.presentation.theme.LukiaAlpha
import io.lukia.shared.presentation.theme.Space
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.isoDayNumber
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private const val DaysInWeek = 7

/** Every mini month is drawn six weeks tall, so the twelve line up in rows. */
private const val WeeksInMiniMonth = 6

/** Where in a mini month the zoom grows from, as a share of its width and height. */
private const val Centre = 0.5f

private val MiniGap: Dp = 2.dp

private val MiniRing: Dp = 1.5.dp

/**
 * A year as its twelve months, each a small calendar shaded day by day.
 *
 * Twelve blocks rather than one strip of 53 weeks, so a month is something a
 * thumb can hit: a phone gives each a third of its width, where a strip left a
 * day five points wide. A month is one target - tapping anywhere in it opens it
 * - because a day is chosen in the month, where days are big enough to choose
 * between.
 *
 * Shaded against the year's busiest day, so one month can be compared with
 * another at a glance.
 *
 * [reachable] are the months there is something to open: from the first one
 * read in to the current one. The rest are drawn, faint, and do nothing.
 */
@Composable
internal fun YearMonths(
    year: PeriodReading,
    selected: LocalDate?,
    columns: Int,
    reachable: ClosedRange<LocalDate>,
    onShowMonth: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val busiest = year.busiestDayMs
    val byMonth = remember(year) { year.days.groupBy { it.date.month } }
    val spoken = pluralStringResource(
        Res.plurals.stats_year_days_spoken,
        year.daysRead,
        year.daysRead,
        year.period.start.year,
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(Space.sm),
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = spoken },
    ) {
        Month.entries.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                row.forEach { month ->
                    val first = LocalDate(year.period.start.year, month, 1)

                    MiniMonth(
                        month = month,
                        days = byMonth.getValue(month),
                        busiestMs = busiest,
                        selected = selected,
                        enabled = first >= reachable.start && first <= reachable.endInclusive,
                        onClick = { onShowMonth(first) },
                        modifier = Modifier.weight(1f),
                    )
                }

                // A short last row keeps its blocks the width of the rest.
                repeat(columns - row.size) { Column(modifier = Modifier.weight(1f)) {} }
            }
        }
    }
}

/** Where [month]'s block sits in a grid [columns] across, for the zoom to grow from. */
internal fun miniMonthOrigin(month: Month, columns: Int): TransformOrigin {
    val index = month.ordinal
    val rows = (Month.entries.size + columns - 1) / columns

    return TransformOrigin(
        pivotFractionX = (index % columns + Centre) / columns,
        pivotFractionY = (index / columns + Centre) / rows,
    )
}

@Composable
private fun MiniMonth(
    month: Month,
    days: List<DayReading>,
    busiestMs: Long,
    selected: LocalDate?,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.small
    val label = stringResource(Res.string.stats_show_month, monthName(month, MonthForm.Full))
    val isCurrent = days.any { it.isToday }
    Column(
        verticalArrangement = Arrangement.spacedBy(Space.xs),
        modifier = modifier
            .clip(shape)
            .pressable(
                interactionSource = remember { MutableInteractionSource() },
                shape = shape,
                onClickLabel = label,
                onClick = onClick,
                enabled = enabled,
            )
            .clearAndSetSemantics {
                contentDescription = label
                role = Role.Button
                if (enabled) {
                    onClick {
                        onClick()
                        true
                    }
                } else {
                    disabled()
                }
            }
            .alpha(if (enabled) 1f else LukiaAlpha.Disabled)
            .padding(Space.xs),
    ) {
        Text(
            text = monthName(month, MonthForm.Short),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isCurrent) colors.primary else colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        MiniDays(days = days, busiestMs = busiestMs, selected = selected)
    }
}

/** A mini month's days, one square each, Monday first, the selected one ringed. */
@Composable
private fun MiniDays(
    days: List<DayReading>,
    busiestMs: Long,
    selected: LocalDate?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val lead = days.first().date.dayOfWeek.isoDayNumber - 1

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(DaysInWeek.toFloat() / WeeksInMiniMonth),
    ) {
        val gap = MiniGap.toPx()
        val cell = minOf(
            (size.width - gap * (DaysInWeek - 1)) / DaysInWeek,
            (size.height - gap * (WeeksInMiniMonth - 1)) / WeeksInMiniMonth,
        )
        val radius = CornerRadius(cell / 4)

        days.forEachIndexed { index, day ->
            val slot = lead + index
            val topLeft = Offset((slot % DaysInWeek) * (cell + gap), (slot / DaysInWeek) * (cell + gap))
            val shade = if (day.isFuture) 0 else shadeOf(day.durationMs, busiestMs)
            val color = when {
                day.isFuture -> colors.onSurfaceVariant.copy(alpha = LukiaAlpha.Hover / 2)
                shade == 0 -> colors.onSurfaceVariant.copy(alpha = LukiaAlpha.Hover)
                else -> colors.primary.copy(alpha = shadeAlpha(shade))
            }

            drawRoundRect(color = color, topLeft = topLeft, size = Size(cell, cell), cornerRadius = radius)

            if (day.date == selected) {
                drawRoundRect(
                    color = colors.onSurface,
                    topLeft = topLeft,
                    size = Size(cell, cell),
                    cornerRadius = radius,
                    style = Stroke(MiniRing.toPx()),
                )
            }
        }
    }
}
