package io.lukia.features.stats.presentation

import androidx.compose.runtime.Composable
import io.lukia.core.infrastructure.MonthForm
import io.lukia.core.infrastructure.monthName
import io.lukia.features.stats.domain.PeriodKind
import io.lukia.features.stats.domain.PeriodReading
import io.lukia.features.stats.domain.ReadingPeriod
import io.lukia.resources.Res
import io.lukia.resources.stats_change_down
import io.lukia.resources.stats_change_down_so_far
import io.lukia.resources.stats_change_up
import io.lukia.resources.stats_change_up_so_far
import io.lukia.resources.stats_last_week
import io.lukia.resources.stats_month_year
import io.lukia.resources.stats_period_month
import io.lukia.resources.stats_period_week
import io.lukia.resources.stats_period_year
import io.lukia.resources.stats_this_week
import io.lukia.resources.stats_weeks_ago
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.absoluteValue

/** What a segment of the period control is called. */
@Composable
internal fun periodKindLabel(kind: PeriodKind): String = stringResource(
    when (kind) {
        PeriodKind.Week -> Res.string.stats_period_week
        PeriodKind.Month -> Res.string.stats_period_month
        PeriodKind.Year -> Res.string.stats_period_year
    },
)

/**
 * A period's name over its chart.
 *
 * A week is named by how far back it is rather than by its dates: "3 weeks ago"
 * needs no date format, and how a short date is written is not the same in any
 * two of the languages this app speaks.
 */
@Composable
internal fun periodName(period: ReadingPeriod, offset: Int): String = when (period.kind) {
    PeriodKind.Week -> when (offset) {
        0 -> stringResource(Res.string.stats_this_week)
        -1 -> stringResource(Res.string.stats_last_week)
        else -> pluralStringResource(Res.plurals.stats_weeks_ago, -offset, -offset)
    }

    PeriodKind.Month -> stringResource(
        Res.string.stats_month_year,
        monthName(period.start.month, MonthForm.Full),
        period.start.year,
    )

    PeriodKind.Year -> period.start.year.toString()
}

/**
 * How a month or year compares with the one before, or null when there is
 * nothing before it to compare with or no change worth a line.
 */
@Composable
internal fun changeLine(reading: PeriodReading): String? {
    val change = reading.changePercent?.takeIf { it != 0 } ?: return null
    val previous = reading.period.shiftedBy(-1)
    val name = when (previous.kind) {
        PeriodKind.Month -> monthName(previous.start.month, MonthForm.Full)
        else -> previous.start.year.toString()
    }
    val resource = when {
        change > 0 && reading.isCurrent -> Res.string.stats_change_up_so_far
        change > 0 -> Res.string.stats_change_up
        reading.isCurrent -> Res.string.stats_change_down_so_far
        else -> Res.string.stats_change_down
    }

    return stringResource(resource, change.absoluteValue, name)
}
