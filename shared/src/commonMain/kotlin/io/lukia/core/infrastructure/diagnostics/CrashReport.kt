package io.lukia.core.infrastructure.diagnostics

import io.lukia.LukiaVersion
import io.lukia.core.domain.diagnostics.DiagnosticEntry
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Why a failure's own words are not in here.
 *
 * A message is uncontrolled text from whatever threw: a file failure puts the
 * path in it, and on a desktop a path carries the reader's own name. The type
 * and the line it died on are what a maintainer works from anyway, and they are
 * the parts nothing about the reader can reach - the same rule the rest of the
 * record is built on.
 */
private const val MessagesOmitted =
    "Failure messages are left out: they carry file paths and request addresses. " +
        "The type and the line are here instead."

/** Past this a trace is the same frames again, in a file somebody has to read. */
private const val MaxFrames = 30

/** Deep enough for a wrapped failure and its cause, without unrolling a library. */
private const val MaxCauses = 3

/**
 * What is left behind when the app dies, for the reader to find next time they
 * open it.
 *
 * Written to a file rather than held like the rest of the record, because the
 * buffer dies with the process and this is about the failure that kills it. It
 * is app-private, never sent, and the reader's to read, copy or delete.
 *
 * [entries] is whatever the buffer held, which is nothing unless diagnostics
 * were already on - the consent split, needing no code: a stack trace is about
 * the app, the breadcrumbs are about a reader's afternoon.
 */
fun crashReport(
    failure: Throwable,
    entries: List<DiagnosticEntry>,
    platformName: String,
    at: Instant,
    appVersion: String = LukiaVersion,
): String = buildString {
    append("Lukia ")
    append(appVersion)
    append(" crash - ")
    append(platformName)
    append(" - ")
    appendLine(at.dayAndTime())

    appendLine(MessagesOmitted)
    appendLine()

    append(failure.typeAndFrames())

    appendLine()
    appendLine()

    if (entries.isEmpty()) {
        append(
            "Nothing was recorded before it. Turn on Diagnostics in Settings to collect " +
                "that next time.",
        )

        return@buildString
    }

    append("What led up to it:")

    entries.forEach {
        appendLine()
        append(it.asReportLine())
    }
}

/**
 * The failure as its type and the lines it passed through, and its causes the
 * same way - never as its message. See [MessagesOmitted].
 */
private fun Throwable.typeAndFrames(): String = buildString {
    var current: Throwable? = this@typeAndFrames
    var depth = 0

    while (current != null && depth < MaxCauses) {
        if (depth > 0) append("caused by ")

        appendLine(current::class.simpleName ?: "unknown failure")

        current.ownFrames().forEach { appendLine("  $it") }

        current = current.cause
        depth++
    }
}

/**
 * The `at` lines of one throwable's trace, and only those.
 *
 * Filtered rather than taken whole because the first line of a rendered trace is
 * the type and the message together, and the message is the half that must not
 * travel.
 */
private fun Throwable.ownFrames(): List<String> =
    stackTraceToString()
        .lineSequence()
        .map { it.trim() }
        .filter { it.startsWith("at ") }
        .take(MaxFrames)
        .toList()

/**
 * The date as well as the time, unlike the rest of the record.
 *
 * Everything else is one run of the app and reads as a sequence. A crash is read
 * on a later run, possibly days later, and "did this happen just now or last
 * week" is the first thing anybody asks about it.
 */
private fun Instant.dayAndTime(): String {
    val local = toLocalDateTime(TimeZone.currentSystemDefault())

    return "${local.date} ${timeOfDay()}"
}
