package io.lukia.core.infrastructure.diagnostics

import io.lukia.core.domain.diagnostics.DiagnosticEntry
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * What the record promises about itself, carried in the record.
 *
 * Written for two readers at once. The person handing it over should be able to
 * see what they are handing over without reading every line of it, and the
 * person receiving it should know not to ask for the parts that are not there.
 */
private const val ReportPromise =
    "This record holds no book text, titles, authors, file names, addresses or keys."

/**
 * One entry as the single line it is shown and copied as.
 *
 * The same line does both jobs on purpose: what a reader is shown before they
 * copy has to be what they copy, or reviewing it is a formality rather than a
 * decision.
 */
fun DiagnosticEntry.asReportLine(): String = buildString {
    append(at.timeOfDay())
    append("  ")
    append(level.name.uppercase().padEnd(LevelWidth))
    append(' ')
    append(area.name.padEnd(AreaWidth))
    append(' ')
    append(event)

    detail?.let {
        append("  ")
        append(it)
    }

    causeType?.let {
        append("  ")
        append(it)
    }
}

/**
 * The whole record, as the text a reader hands to somebody who was not there.
 *
 * Plain text and one line per entry, because the two things anybody does with
 * this are read it down the page and search it - and because there is nowhere
 * for it to go but an issue or a message the reader writes themselves.
 *
 * [platformName] comes from `getPlatform().name`, which is the one piece of
 * context here that is not an entry: half of what makes a report actionable is
 * knowing which of the three builds produced it.
 */
fun diagnosticReport(entries: List<DiagnosticEntry>, platformName: String): String = buildString {
    append("Lukia diagnostics - ")
    append(platformName)
    append(" - ")
    append(entries.size)
    append(if (entries.size == 1) " entry" else " entries")
    appendLine()

    appendLine(ReportPromise)

    if (entries.isEmpty()) {
        appendLine()
        append("Nothing was recorded.")
        return@buildString
    }

    entries.forEach {
        appendLine()
        append(it.asReportLine())
    }
}

/**
 * The time of day, in the reader's own zone.
 *
 * A date would say nothing: the record is a single run of the app, and every
 * entry in it is from the same afternoon as the one that prompted it. What a
 * reader and a maintainer both need is the gap between two entries.
 */
private fun kotlin.time.Instant.timeOfDay(): String {
    val local = toLocalDateTime(TimeZone.currentSystemDefault())

    return "${local.hour.padded()}:${local.minute.padded()}:${local.second.padded()}"
}

private fun Int.padded(): String = toString().padStart(2, '0')

/** Held so the events line up down the page, which is what makes them scannable. */
private const val LevelWidth = 7
private const val AreaWidth = 11
