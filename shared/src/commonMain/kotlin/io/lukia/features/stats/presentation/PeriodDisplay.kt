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
import io.lukia.resources.stats_month_year
import org.jetbrains.compose.resources.stringResource
import kotlin.math.absoluteValue

/** A period's name over its calendar: "November 2026", or "2026". */
@Composable
internal fun periodName(period: ReadingPeriod): String = when (period.kind) {
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
        PeriodKind.Year -> previous.start.year.toString()
    }
    val resource = when {
        change > 0 && reading.isCurrent -> Res.string.stats_change_up_so_far
        change > 0 -> Res.string.stats_change_up
        reading.isCurrent -> Res.string.stats_change_down_so_far
        else -> Res.string.stats_change_down
    }

    return stringResource(resource, change.absoluteValue, name)
}
