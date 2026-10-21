package io.lukia.core.infrastructure

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.isoDayNumber
import platform.Foundation.NSDateFormatter

internal actual fun weekdayName(dayOfWeek: DayOfWeek, form: WeekdayForm): String {
    val formatter = NSDateFormatter()
    val symbols = when (form) {
        WeekdayForm.Full -> formatter.weekdaySymbols
        WeekdayForm.Short -> formatter.shortWeekdaySymbols
        WeekdayForm.Narrow -> formatter.veryShortWeekdaySymbols
    }

    // Cocoa's symbols begin on Sunday; an ISO day number begins on Monday and
    // ends on Sunday at seven. The remainder maps one onto the other without a
    // table: Monday's 1 is index 1, and Sunday's 7 wraps to 0.
    return symbols[dayOfWeek.isoDayNumber % DaysInAWeek] as String
}

private const val DaysInAWeek = 7
