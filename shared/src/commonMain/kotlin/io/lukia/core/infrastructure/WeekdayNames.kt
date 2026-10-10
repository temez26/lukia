package io.lukia.core.infrastructure

import kotlinx.datetime.DayOfWeek

/**
 * What the platform calls a day of the week, in the language the device is set to.
 *
 * Asked rather than tabulated. The chart used to read the day out of Kotlin's
 * enum - `MONDAY` lower-cased and capitalised, English however the app is set -
 * and truncated that for the short form. Truncation is right for English and
 * wrong almost everywhere else: an abbreviation is not always a prefix, and a
 * script with no three-letter form has none to cut.
 *
 * Every platform already holds these names for every language it ships, so there
 * is nothing here for a translator to keep in step.
 */
internal expect fun weekdayName(dayOfWeek: DayOfWeek, abbreviated: Boolean): String
