package io.lukia.core.infrastructure

import java.text.NumberFormat
import java.util.Locale

internal actual fun formatDecimal(value: Double, fractionDigits: Int): String =
    NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = fractionDigits
    }.format(value)

internal actual fun formatCount(value: Int): String =
    NumberFormat.getIntegerInstance(Locale.getDefault()).format(value)
