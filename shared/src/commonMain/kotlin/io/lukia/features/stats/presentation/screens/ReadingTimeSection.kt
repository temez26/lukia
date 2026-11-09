package io.lukia.features.stats.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import io.lukia.core.infrastructure.MonthForm
import io.lukia.core.infrastructure.formatCount
import io.lukia.core.infrastructure.monthName
import io.lukia.features.stats.domain.PeriodKind
import io.lukia.features.stats.domain.PeriodReading
import io.lukia.features.stats.domain.ReadingHabits
import io.lukia.features.stats.domain.ReadingHistory
import io.lukia.features.stats.domain.periodReading
import io.lukia.features.stats.presentation.PeriodSelection
import io.lukia.features.stats.presentation.changeLine
import io.lukia.features.stats.presentation.components.MonthCalendar
import io.lukia.features.stats.presentation.components.ReadingChart
import io.lukia.features.stats.presentation.components.StatFigure
import io.lukia.features.stats.presentation.components.SteadyStack
import io.lukia.features.stats.presentation.components.YearGrid
import io.lukia.features.stats.presentation.components.dayBars
import io.lukia.features.stats.presentation.components.monthBars
import io.lukia.features.stats.presentation.periodKindLabel
import io.lukia.features.stats.presentation.periodName
import io.lukia.features.stats.presentation.readingTime
import io.lukia.features.stats.presentation.readingTimeOrDash
import io.lukia.features.stats.presentation.streakLine
import io.lukia.resources.Res
import io.lukia.resources.stats_best_month
import io.lukia.resources.stats_daily_average
import io.lukia.resources.stats_days_of
import io.lukia.resources.stats_days_read
import io.lukia.resources.stats_next_period
import io.lukia.resources.stats_previous_period
import io.lukia.resources.stats_reading_time
import io.lukia.resources.stats_this_week_footnote
import io.lukia.resources.stats_time_dash
import io.lukia.resources.stats_time_read
import io.lukia.resources.stats_today
import io.lukia.resources.stats_today_spoken
import io.lukia.shared.presentation.components.controls.LukiaIconButton
import io.lukia.shared.presentation.components.controls.SegmentedControl
import io.lukia.shared.presentation.components.icons.BackChevron
import io.lukia.shared.presentation.components.icons.ForwardChevron
import io.lukia.shared.presentation.components.surface.BadgeTone
import io.lukia.shared.presentation.components.surface.GroupSection
import io.lukia.shared.presentation.components.surface.LukiaDivider
import io.lukia.shared.presentation.components.surface.StatusBadge
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource

/**
 * Where the time went: today first, then any week, month or year since the app
 * began counting.
 *
 * One card that changes what it shows rather than a card per span, so the page
 * stays as short as it was when it showed only the week. A week is bars, because
 * its question is the shape of seven days; a month is a calendar, because its
 * question is which days; a year is both, months as bars over a grid of days.
 *
 * Left out entirely until there is something to show, for the reason it always
 * was: a card of zeroes is a reproach rather than a statistic.
 */
@Composable
internal fun ReadingTimeSection(
    habits: ReadingHabits,
    history: ReadingHistory,
    selection: PeriodSelection,
    actions: StatsActions,
    groupColor: Color,
    modifier: Modifier = Modifier,
) {
    if (habits.isEmpty) return

    // The spans not chosen are worked out too, at their current period, only to
    // be measured: see the stack below.
    val readings = remember(history, selection) {
        PeriodKind.entries.associateWith { kind ->
            history.periodReading(kind, if (kind == selection.kind) selection.offset else 0)
        }
    }
    val reading = readings.getValue(selection.kind)

    GroupSection(
        title = stringResource(Res.string.stats_reading_time),
        color = groupColor,
        footnote = stringResource(Res.string.stats_this_week_footnote),
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.xl)) {
            TodayHeadline(habits = habits)

            SegmentedControl(
                options = PeriodKind.entries,
                selected = selection.kind,
                label = { periodKindLabel(it) },
                onSelect = actions.onSelectPeriod,
            )

            PeriodPager(reading = reading, offset = selection.offset, onStep = actions.onStepPeriod)

            // Every span is laid out and only the chosen one is seen, so the card
            // is as tall as the tallest of them and switching never moves the
            // cards below it.
            SteadyStack {
                readings.forEach { (kind, shown) ->
                    PeriodBody(
                        reading = shown,
                        modifier = if (kind == selection.kind) {
                            Modifier
                        } else {
                            Modifier
                                .alpha(0f)
                                .clearAndSetSemantics { }
                        },
                    )
                }
            }
        }
    }
}

/** What a span holds, under its name: the chart or calendar, then its figures. */
@Composable
private fun PeriodBody(
    reading: PeriodReading,
    modifier: Modifier = Modifier,
) {
    val kind = reading.period.kind

    Column(
        verticalArrangement = Arrangement.spacedBy(Space.xl),
        modifier = modifier.fillMaxWidth(),
    ) {
        when (kind) {
            PeriodKind.Week -> ReadingChart(bars = dayBars(reading.days))
            PeriodKind.Month -> MonthCalendar(month = reading)
            PeriodKind.Year -> {
                ReadingChart(bars = monthBars(reading.months))
                YearGrid(year = reading)
            }
        }

        if (kind != PeriodKind.Week) {
            val line = changeLine(reading)

            // Kept as an empty line when there is nothing to compare with, so
            // paging onto such a period doesn't shorten the card.
            Text(
                text = line.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = if (line == null) Modifier.clearAndSetSemantics { } else Modifier,
            )
        }

        LukiaDivider()

        PeriodTotals(reading = reading)

        if (kind == PeriodKind.Month) {
            BestRunRow(days = reading.longestRun)
        }
    }
}

/**
 * Today's reading, set as the one figure on the page worth reading first, with
 * the run of days it belongs to beside it.
 *
 * Above the period control rather than inside the week, because it is the only
 * number here that can still be changed this evening, whichever span is shown.
 */
@Composable
private fun TodayHeadline(
    habits: ReadingHabits,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        // Read before the modifier chain: `clearAndSetSemantics` is not a
        // composable scope.
        val spoken = stringResource(Res.string.stats_today_spoken, readingTime(habits.todayMs))

        Column(
            modifier = Modifier
                .weight(1f)
                .clearAndSetSemantics { contentDescription = spoken },
        ) {
            Text(
                text = stringResource(Res.string.stats_today),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = readingTimeOrDash(habits.todayMs),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Space.xxs),
            )
        }

        // Only a run is worth a badge; the absence of one needs no announcing.
        if (habits.currentStreak > 0) {
            StatusBadge(
                text = streakLine(habits.currentStreak),
                tone = BadgeTone.Accent,
                modifier = Modifier.padding(start = Space.lg),
            )
        }
    }
}

/** The period's name between the way back and the way forward. */
@Composable
private fun PeriodPager(
    reading: PeriodReading,
    offset: Int,
    onStep: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
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

        Text(
            text = periodName(reading.period, offset),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )

        LukiaIconButton(
            label = stringResource(Res.string.stats_next_period),
            onClick = { onStep(1) },
            enabled = reading.hasLater,
        ) { tint, glyph ->
            ForwardChevron(tint = tint, modifier = glyph)
        }
    }
}

/**
 * What the period comes to.
 *
 * A week and a month say how a day of them usually went; a year has too many
 * days for that to mean much, and says which month carried it instead - by its
 * short name, which fits a third of a phone at the largest type where
 * "September" does not.
 */
@Composable
private fun PeriodTotals(
    reading: PeriodReading,
    modifier: Modifier = Modifier,
) {
    val daysRead = stringResource(Res.string.stats_days_of, reading.daysRead, reading.daysReached)

    Row(
        horizontalArrangement = Arrangement.spacedBy(Space.lg),
        modifier = modifier.fillMaxWidth(),
    ) {
        Figure(value = readingTimeOrDash(reading.totalMs), label = stringResource(Res.string.stats_time_read))

        if (reading.period.kind == PeriodKind.Year) {
            Figure(value = formatCount(reading.daysRead), label = stringResource(Res.string.stats_days_read))
            Figure(
                value = reading.busiestMonth?.let { monthName(it.month, MonthForm.Short) }
                    ?: stringResource(Res.string.stats_time_dash),
                label = stringResource(Res.string.stats_best_month),
            )
        } else {
            Figure(
                value = readingTimeOrDash(reading.dailyAverageMs),
                label = stringResource(Res.string.stats_daily_average),
            )
            Figure(value = daysRead, label = stringResource(Res.string.stats_days_read))
        }
    }
}

@Composable
private fun RowScope.Figure(value: String, label: String) {
    StatFigure(
        value = value,
        label = label,
        modifier = Modifier.weight(1f),
        style = MaterialTheme.typography.titleMedium,
    )
}
