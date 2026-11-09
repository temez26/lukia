package io.lukia.core.infrastructure

import kotlinx.datetime.Month

/**
 * What the platform calls a month, in the language the device is set to, in the
 * form that stands alone ("marraskuu", not the "marraskuuta" of a date).
 *
 * Asked rather than tabulated, for the reason [weekdayName] is.
 */
internal expect fun monthName(month: Month, form: MonthForm): String

/** How much of a month's name is wanted. */
internal enum class MonthForm {
    /** "November", over a calendar. */
    Full,

    /** "Nov", under a bar. */
    Short,
}
