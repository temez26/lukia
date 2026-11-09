package io.lukia.core.infrastructure

import kotlinx.datetime.Month
import kotlinx.datetime.number
import platform.Foundation.NSDateFormatter

internal actual fun monthName(month: Month, form: MonthForm): String {
    val formatter = NSDateFormatter()
    val symbols = when (form) {
        MonthForm.Full -> formatter.standaloneMonthSymbols
        MonthForm.Short -> formatter.shortStandaloneMonthSymbols
    }

    return (symbols[month.number - 1] as String).replaceFirstChar { it.uppercaseChar() }
}
