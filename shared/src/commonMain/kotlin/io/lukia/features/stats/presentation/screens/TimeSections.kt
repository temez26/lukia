package io.lukia.features.stats.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import io.lukia.core.infrastructure.formatCount
import io.lukia.features.stats.domain.ReadingHabits
import io.lukia.features.stats.presentation.components.StatFigure
import io.lukia.features.stats.presentation.readingTimeOrDash
import io.lukia.features.stats.presentation.streakLine
import io.lukia.resources.Res
import io.lukia.resources.stats_all_time
import io.lukia.resources.stats_best_day
import io.lukia.resources.stats_longest_run
import io.lukia.resources.stats_longest_run_spoken
import io.lukia.resources.stats_sittings
import io.lukia.resources.stats_time_read
import io.lukia.shared.presentation.components.surface.GroupSection
import io.lukia.shared.presentation.components.surface.LukiaDivider
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

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
internal fun BestRunRow(
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
