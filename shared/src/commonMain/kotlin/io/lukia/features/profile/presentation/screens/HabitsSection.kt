package io.lukia.features.profile.presentation.screens

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
import io.lukia.core.presentation.UiState
import io.lukia.features.profile.domain.ReadingHabits
import io.lukia.features.profile.domain.ReadingSummary
import io.lukia.features.profile.presentation.components.ReadingChart
import io.lukia.features.profile.presentation.readingTime
import io.lukia.features.profile.presentation.streakLine
import io.lukia.shared.presentation.components.GroupSection
import io.lukia.shared.presentation.components.LukiaDivider
import io.lukia.shared.presentation.theme.Space

/**
 * How much time the reading has actually taken, and when it happened.
 *
 * Left out entirely until there is something to show. An app that has just begun
 * counting has nothing to say here, and a card of zeroes on a profile is a
 * reproach rather than a statistic - the figures above it already say what is on
 * the shelf.
 *
 * Time is not shown while the rest is still being read, for the same reason the
 * activity list is not: one wait on a page is a page loading, and three is a
 * page that looks broken.
 */
@Composable
internal fun HabitsSection(
    summary: UiState<ReadingSummary>,
    groupColor: Color,
    modifier: Modifier = Modifier,
) {
    val habits = (summary as? UiState.Success)?.data?.habits ?: return

    if (habits.isEmpty) return

    GroupSection(
        title = "Time spent reading",
        color = groupColor,
        footnote = "Counted while a book is open and being turned, " +
            "and only from the day this app started keeping track.",
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.xl)) {
            TimeHeadline(habits = habits)

            ReadingChart(days = habits.days, busiestMs = habits.busiestDayMs)

            LukiaDivider()

            StreakLine(habits = habits)
        }
    }
}

/**
 * The three figures worth leading with, largest first.
 *
 * Today, this week, and all of it - which is the order somebody reads them in
 * when the question is "have I read enough lately". The total goes last because
 * it only grows, and a number that only grows is the least interesting thing on
 * any page it is on.
 */
@Composable
private fun TimeHeadline(
    habits: ReadingHabits,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Space.lg),
        modifier = modifier.fillMaxWidth(),
    ) {
        TimeFigure(value = readingTime(habits.todayMs), label = "Today", modifier = Modifier.weight(1f))
        TimeFigure(value = readingTime(habits.weekMs), label = "This week", modifier = Modifier.weight(1f))
        TimeFigure(value = readingTime(habits.totalMs), label = "All time", modifier = Modifier.weight(1f))
    }
}

@Composable
private fun TimeFigure(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.clearAndSetSemantics { contentDescription = "$label: $value" },
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(top = Space.xxs),
        )
    }
}

/**
 * The run of days, and the best one there has been.
 *
 * The best is only mentioned once it is worth beating: a reader on their first
 * day being told their record is one day is the app talking to itself.
 */
@Composable
private fun StreakLine(
    habits: ReadingHabits,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = streakLine(habits.currentStreak),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        if (habits.longestStreak > habits.currentStreak) {
            Text(
                text = "Best: ${habits.longestStreak} days",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
