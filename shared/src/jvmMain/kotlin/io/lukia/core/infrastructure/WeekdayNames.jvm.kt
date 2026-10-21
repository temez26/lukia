package io.lukia.core.infrastructure

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.isoDayNumber
import java.time.format.TextStyle
import java.util.Locale

internal actual fun weekdayName(dayOfWeek: DayOfWeek, form: WeekdayForm): String =
    java.time.DayOfWeek.of(dayOfWeek.isoDayNumber).getDisplayName(
        when (form) {
            WeekdayForm.Full -> TextStyle.FULL
            WeekdayForm.Short -> TextStyle.SHORT
            WeekdayForm.Narrow -> TextStyle.NARROW
        },
        Locale.getDefault(),
    )
