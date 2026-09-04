package io.lukia.core.infrastructure

import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle

internal actual fun formatDecimal(value: Double, fractionDigits: Int): String {
    val formatter = NSNumberFormatter().apply {
        numberStyle = NSNumberFormatterDecimalStyle
        minimumFractionDigits = 0u
        maximumFractionDigits = fractionDigits.toULong()
    }

    return formatter.stringFromNumber(NSNumber(double = value)) ?: value.toString()
}

internal actual fun formatCount(value: Int): String {
    val formatter = NSNumberFormatter().apply {
        numberStyle = NSNumberFormatterDecimalStyle
        maximumFractionDigits = 0u
    }

    return formatter.stringFromNumber(NSNumber(int = value)) ?: value.toString()
}
