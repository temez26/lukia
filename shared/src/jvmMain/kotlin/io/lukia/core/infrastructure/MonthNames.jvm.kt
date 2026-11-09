package io.lukia.core.infrastructure

import kotlinx.datetime.Month
import kotlinx.datetime.number
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// `L` is the stand-alone month. Through `SimpleDateFormat` rather than
// java.time's `FULL_STANDALONE`, which some runtimes answer with the month's
// number instead of its name.
internal actual fun monthName(month: Month, form: MonthForm): String {
    val pattern = when (form) {
        MonthForm.Full -> "LLLL"
        MonthForm.Short -> "LLL"
    }
    val calendar = Calendar.getInstance().apply {
        clear()
        set(2000, month.number - 1, 1)
    }

    return SimpleDateFormat(pattern, Locale.getDefault()).format(calendar.time)
        .replaceFirstChar { it.titlecase(Locale.getDefault()) }
}
