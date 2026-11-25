package io.lukia.features.stats.presentation.screens

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.core.infrastructure.MonthForm
import io.lukia.core.infrastructure.monthName
import io.lukia.features.stats.domain.PeriodKind
import io.lukia.features.stats.domain.PeriodReading
import io.lukia.features.stats.domain.ReadingHabits
import io.lukia.features.stats.domain.ReadingHistory
import io.lukia.features.stats.domain.ReadingPeriod
import io.lukia.features.stats.domain.periodReading
import io.lukia.features.stats.presentation.CalendarSelection
import io.lukia.features.stats.presentation.components.DayCard
import io.lukia.features.stats.presentation.periodName
import io.lukia.resources.Res
import io.lukia.resources.stats_back_to_year
import io.lukia.resources.stats_next_period
import io.lukia.resources.stats_previous_period
import io.lukia.resources.stats_reading_time
import io.lukia.resources.stats_show_month
import io.lukia.resources.stats_this_week_footnote
import io.lukia.shared.presentation.components.controls.LukiaIconButton
import io.lukia.shared.presentation.components.controls.pressable
import io.lukia.shared.presentation.components.icons.BackChevron
import io.lukia.shared.presentation.components.icons.ForwardChevron
import io.lukia.shared.presentation.components.surface.GroupSection
import io.lukia.shared.presentation.theme.Space
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

/** From this width the card sets its calendar beside the day and the figures. */
private val SplitWidth: Dp = 560.dp

/** How much of a wide card the calendar takes; the day and figures have the rest. */
private const val CalendarShare = 0.6f

/** Mini months across, on a phone and on a card wide enough to split. */
private const val NarrowYearColumns = 3
private const val WideYearColumns = 4

/**
 * Where the time went, as one calendar: a month, or zoomed out the year around
 * it, with one day of it selected and exactly how long was read on it.
 *
 * One view rather than a tab per span: the year, a month and a day are the same
 * calendar at three distances, and zooming keeps the reader's place where tabs
 * dropped it. Opens on this month with today selected, since somebody looking has
 * usually just put a book down.
 *
 * Left out entirely until there is something to show, for the reason it always
 * was: a card of zeroes is a reproach rather than a statistic.
 */
@Composable
internal fun ReadingTimeSection(
    habits: ReadingHabits,
    history: ReadingHistory,
    selection: CalendarSelection,
    actions: StatsActions,
    groupColor: Color,
    modifier: Modifier = Modifier,
) {
    if (habits.isEmpty) return

    val period = selection.shownPeriod(history)
    val reading = remember(history, period) { history.periodReading(period) }
    val selected = selection.selectedDay(history, period)
    val day = reading.days.first { it.date == selected }

    GroupSection(
        title = stringResource(Res.string.stats_reading_time),
        color = groupColor,
        footnote = stringResource(Res.string.stats_this_week_footnote),
        modifier = modifier,
    ) {
        BoxWithConstraints {
            val split = maxWidth >= SplitWidth
            val dayCard: @Composable () -> Unit = {
                DayCard(
                    day = day,
                    busiestMs = reading.busiestDayMs,
                    averageMs = reading.dailyAverageMs,
                    streak = habits.currentStreak,
                )
            }
            val calendar: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    PeriodPager(
                        reading = reading,
                        onStep = { by -> actions.onShowMonth(stepped(selection.shownMonth(history), period, by)) },
                        zoomLabel = zoomLabel(selection.zoomed, selection.shownMonth(history)),
                        onZoom = { if (selection.zoomed) actions.onZoomOut(selected) else actions.onZoomIn() },
                    )

                    CalendarPages(
                        history = history,
                        selection = selection,
                        selected = selected,
                        columns = if (split) WideYearColumns else NarrowYearColumns,
                        actions = actions,
                    )
                }
            }

            CardLayout(
                split = split,
                day = dayCard,
                calendar = calendar,
                details = { PeriodFigures(reading = reading) },
            )
        }
    }
}

/**
 * The card's parts in reading order: on a phone the day, the calendar, then the
 * figures; on a wide card the calendar on the start side and the rest beside it.
 */
@Composable
private fun CardLayout(
    split: Boolean,
    day: @Composable () -> Unit,
    calendar: @Composable () -> Unit,
    details: @Composable () -> Unit,
) {
    // Movable, so resizing a window across the split keeps each part's state -
    // the zoom, the page, the rolling figures - rather than starting it again.
    // Each reads its slot as it is now, while staying the same content to move.
    val currentDay by rememberUpdatedState(day)
    val currentCalendar by rememberUpdatedState(calendar)
    val currentDetails by rememberUpdatedState(details)
    val dayPart = remember { movableContentOf { currentDay() } }
    val calendarPart = remember { movableContentOf { currentCalendar() } }
    val detailsPart = remember { movableContentOf { currentDetails() } }

    if (split) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.xxl)) {
            Box(modifier = Modifier.weight(CalendarShare)) { calendarPart() }

            Column(
                verticalArrangement = Arrangement.spacedBy(Space.xl),
                modifier = Modifier.weight(1f - CalendarShare),
            ) {
                dayPart()
                detailsPart()
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(Space.xl)) {
            dayPart()
            calendarPart()
            detailsPart()
        }
    }
}

/** What tapping the period's name does: back to the year, or into [month]. */
@Composable
private fun zoomLabel(zoomed: Boolean, month: ReadingPeriod): String = if (zoomed) {
    stringResource(Res.string.stats_back_to_year, month.start.year)
} else {
    stringResource(Res.string.stats_show_month, monthName(month.start.month, MonthForm.Full))
}

/**
 * The first of the month [by] steps from [month]: a month at a time when one is
 * shown, a year at a time when the year is.
 */
private fun stepped(month: ReadingPeriod, shown: ReadingPeriod, by: Int): LocalDate = when (shown.kind) {
    PeriodKind.Month -> month.shiftedBy(by).start
    PeriodKind.Year -> LocalDate(month.start.year + by, month.start.month, 1)
}

/**
 * The period's name between the way back and the way forward.
 *
 * The name is also the way between levels: a month's name opens its year, and
 * the year's name goes back into the month it came from. Set in the accent
 * colour, as the app sets what can be tapped in running text.
 */
@Composable
private fun PeriodPager(
    reading: PeriodReading,
    onStep: (Int) -> Unit,
    zoomLabel: String,
    onZoom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.small

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        LukiaIconButton(
            label = stringResource(Res.string.stats_previous_period),
            onClick = { onStep(-1) },
            enabled = reading.hasEarlier,
        ) { tint, glyph ->
            BackChevron(tint = tint, modifier = glyph)
        }

        Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f)) {
            Text(
                text = periodName(reading.period),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(shape)
                    .pressable(
                        interactionSource = remember { MutableInteractionSource() },
                        shape = shape,
                        onClickLabel = zoomLabel,
                        onClick = onZoom,
                    )
                    .semantics { heading() }
                    .padding(horizontal = Space.md, vertical = Space.sm),
            )
        }

        LukiaIconButton(
            label = stringResource(Res.string.stats_next_period),
            onClick = { onStep(1) },
            enabled = reading.hasLater,
        ) { tint, glyph ->
            ForwardChevron(tint = tint, modifier = glyph)
        }
    }
}
