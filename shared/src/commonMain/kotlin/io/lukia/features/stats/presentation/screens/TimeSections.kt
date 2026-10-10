package io.lukia.features.stats.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import io.lukia.core.infrastructure.formatCount
import io.lukia.features.stats.domain.ReadingHabits
import io.lukia.features.stats.presentation.components.ReadingChart
import io.lukia.features.stats.presentation.components.StatFigure
import io.lukia.features.stats.presentation.readingTime
import io.lukia.features.stats.presentation.readingTimeOrDash
import io.lukia.features.stats.presentation.streakLine
import io.lukia.resources.Res
import io.lukia.resources.stats_all_time
import io.lukia.resources.stats_best_day
import io.lukia.resources.stats_daily_average
import io.lukia.resources.stats_longest_run
import io.lukia.resources.stats_longest_run_spoken
import io.lukia.resources.stats_sittings
import io.lukia.resources.stats_this_week
import io.lukia.resources.stats_this_week_footnote
import io.lukia.resources.stats_time_read
import io.lukia.resources.stats_today
import io.lukia.resources.stats_today_spoken
import io.lukia.shared.presentation.components.surface.BadgeTone
import io.lukia.shared.presentation.components.surface.GroupSection
import io.lukia.shared.presentation.components.surface.LukiaDivider
import io.lukia.shared.presentation.components.surface.StatusBadge
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The week so far: what today is worth, the shape of the seven days around it,
 * and what the week averages out to.
 *
 * Left out entirely until there is something to show. An app that has just begun
 * counting has nothing to say here, and a card of zeroes is a reproach rather
 * than a statistic - the library figures below already say what is on the shelf.
 */
@Composable
internal fun ThisWeekSection(
    habits: ReadingHabits,
    groupColor: Color,
    modifier: Modifier = Modifier,
) {
    if (habits.isEmpty) return

    GroupSection(
        title = stringResource(Res.string.stats_this_week),
        color = groupColor,
        footnote = stringResource(Res.string.stats_this_week_footnote),
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.xl)) {
            TodayHeadline(habits = habits)

            ReadingChart(days = habits.days, busiestMs = habits.busiestDayMs)

            LukiaDivider()

            WeekTotals(habits = habits)
        }
    }
}

/**
 * Today's reading, set as the one figure on the page worth reading first, with
 * the run of days it belongs to beside it.
 *
 * Today rather than the week's total, because it is the only number here that
 * can still be changed this evening. The label sits above the figure rather than
 * under it, so the eye lands on the hours rather than on the word "Today".
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

        StatusBadge(
            text = streakLine(habits.currentStreak),
            tone = if (habits.currentStreak > 0) BadgeTone.Accent else BadgeTone.Neutral,
            modifier = Modifier.padding(start = Space.lg),
        )
    }
}

/**
 * What the week comes to, under the chart that shows how it got there.
 *
 * The average is beside the total rather than instead of it because the two
 * answer different questions - how much this week has held, and what a day of it
 * usually looks like - and neither can be worked out from the other without
 * knowing what day it is.
 */
@Composable
private fun WeekTotals(
    habits: ReadingHabits,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Space.lg),
        modifier = modifier.fillMaxWidth(),
    ) {
        StatFigure(
            value = readingTimeOrDash(habits.weekMs),
            label = stringResource(Res.string.stats_this_week),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
        )

        StatFigure(
            value = readingTimeOrDash(habits.dailyAverageMs),
            label = stringResource(Res.string.stats_daily_average),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

/**
 * Everything that has ever been counted, which is the part that only grows.
 *
 * Kept apart from the week and set below it, because a figure that only ever
 * rises answers "how long have I had this app" rather than "how am I reading" -
 * it is context for the week above rather than a rival to it.
 */
@Composable
internal fun AllTimeSection(
    habits: ReadingHabits,
    groupColor: Color,
    modifier: Modifier = Modifier,
) {
    if (habits.isEmpty) return

    GroupSection(
        title = stringResource(Res.string.stats_all_time),
        color = groupColor,
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.xl)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
                StatFigure(
                    value = readingTimeOrDash(habits.totalMs),
                    label = stringResource(Res.string.stats_time_read),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )

                StatFigure(
                    value = formatCount(habits.sittings),
                    label = pluralStringResource(
                        Res.plurals.stats_sittings,
                        habits.sittings,
                    ),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )

                StatFigure(
                    value = readingTimeOrDash(habits.longestDayMs),
                    label = stringResource(Res.string.stats_best_day),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            LukiaDivider()

            BestRunRow(days = habits.longestStreak)
        }
    }
}

/**
 * The longest run of days there has ever been.
 *
 * A row rather than a fourth figure above, because it is measured in days where
 * the others are measured in hours, and four figures in a row with one of them
 * counting something else is a row that has to be read twice.
 */
@Composable
private fun BestRunRow(
    days: Int,
    modifier: Modifier = Modifier,
) {
    // Read before the modifier chain: `clearAndSetSemantics` is not a
    // composable scope.
    val run = stringResource(Res.string.stats_longest_run_spoken, streakLine(days))

    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = run },
    ) {
        Text(
            text = stringResource(Res.string.stats_longest_run),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            text = streakLine(days),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
