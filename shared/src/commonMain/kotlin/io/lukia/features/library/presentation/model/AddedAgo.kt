package io.lukia.features.library.presentation.model

import androidx.compose.runtime.Composable
import io.lukia.resources.Res
import io.lukia.resources.home_added_days_ago
import io.lukia.resources.home_added_months_ago
import io.lukia.resources.home_added_today
import io.lukia.resources.home_added_weeks_ago
import io.lukia.resources.home_added_years_ago
import io.lukia.resources.home_added_yesterday
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.monthsUntil
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearsUntil
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

private const val DaysInWeek = 7

/** Under a month, a book is counted in weeks: "5 weeks ago" is never said. */
private const val WeeksBeforeMonths = 4

/**
 * How long ago a book came onto the shelf, as a reader would say it: by
 * calendar day in their own time zone, so a book added at eleven last night is
 * "yesterday" at eight this morning however few hours have passed.
 */
sealed interface AddedAgo {
    data object Today : AddedAgo

    data object Yesterday : AddedAgo

    data class Days(val count: Int) : AddedAgo

    data class Weeks(val count: Int) : AddedAgo

    data class Months(val count: Int) : AddedAgo

    data class Years(val count: Int) : AddedAgo
}

/**
 * When [addedAt] was, seen from [today], or null for a book whose adding was
 * never recorded. A date in the future - a clock set wrong - is "today" rather
 * than a negative count.
 */
fun addedAgo(addedAt: Long?, today: LocalDate, zone: TimeZone): AddedAgo? {
    addedAt ?: return null
    val added = Instant.fromEpochMilliseconds(addedAt).toLocalDateTime(zone).date
    val days = added.daysUntil(today)

    return when {
        days <= 0 -> AddedAgo.Today
        days == 1 -> AddedAgo.Yesterday
        days < DaysInWeek -> AddedAgo.Days(days)
        added.monthsUntil(today) < 1 -> AddedAgo.Weeks((days / DaysInWeek).coerceAtMost(WeeksBeforeMonths))
        added.yearsUntil(today) < 1 -> AddedAgo.Months(added.monthsUntil(today))
        else -> AddedAgo.Years(added.yearsUntil(today))
    }
}

/** [AddedAgo] in words, for the line under a cover. */
@Composable
fun AddedAgo.label(): String = when (this) {
    AddedAgo.Today -> stringResource(Res.string.home_added_today)
    AddedAgo.Yesterday -> stringResource(Res.string.home_added_yesterday)
    is AddedAgo.Days -> pluralStringResource(Res.plurals.home_added_days_ago, count, count)
    is AddedAgo.Weeks -> pluralStringResource(Res.plurals.home_added_weeks_ago, count, count)
    is AddedAgo.Months -> pluralStringResource(Res.plurals.home_added_months_ago, count, count)
    is AddedAgo.Years -> pluralStringResource(Res.plurals.home_added_years_ago, count, count)
}
