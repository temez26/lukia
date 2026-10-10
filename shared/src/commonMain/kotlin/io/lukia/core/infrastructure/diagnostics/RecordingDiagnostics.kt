package io.lukia.core.infrastructure.diagnostics

import io.lukia.core.domain.diagnostics.DiagnosticArea
import io.lukia.core.domain.diagnostics.DiagnosticEntry
import io.lukia.core.domain.diagnostics.DiagnosticLevel
import io.lukia.core.domain.diagnostics.Diagnostics
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Writes entries into [buffer], and drops them on the floor when [enabled] says
 * the reader has not asked for any of this.
 *
 * The gate is read here rather than by swapping what the app is wired with,
 * because the graph hands out one of these for the life of the process and
 * everything holding a reference would keep the one it was given.
 */
class RecordingDiagnostics(
    private val buffer: DiagnosticBuffer,
    /**
     * Answered per entry. Nothing gates it yet - the buffer is in memory,
     * nothing reads it, and nothing can send it anywhere - so this is what a
     * setting will later answer rather than a setting today.
     */
    private val enabled: () -> Boolean = { true },
    /** Injectable so a test can assert on ordering without waiting for a clock. */
    private val now: () -> Instant = { Clock.System.now() },
) : Diagnostics {

    override fun record(
        level: DiagnosticLevel,
        area: DiagnosticArea,
        event: String,
        detail: String?,
        cause: Throwable?,
    ) {
        if (!enabled()) return

        buffer.add(
            DiagnosticEntry(
                at = now(),
                level = level,
                area = area,
                event = event,
                detail = detail,
                // The type only. See `DiagnosticEntry.causeType` for why the
                // message is the one part of a throwable that must not be kept.
                causeType = cause?.let { it::class.simpleName },
            ),
        )
    }
}
