package io.lukia.core.infrastructure

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.isoDayNumber
import java.time.format.TextStyle
import java.util.Locale

internal actual fun weekdayName(dayOfWeek: DayOfWeek, abbreviated: Boolean): String =
    java.time.DayOfWeek.of(dayOfWeek.isoDayNumber).getDisplayName(
        if (abbreviated) TextStyle.SHORT else TextStyle.FULL,
        Locale.getDefault(),
    )
