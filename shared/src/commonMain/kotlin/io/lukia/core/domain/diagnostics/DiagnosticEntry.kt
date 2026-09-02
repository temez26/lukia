package io.lukia.core.domain.diagnostics

import kotlin.time.Instant

/**
 * One thing worth knowing about, as it will be read back weeks later by
 * somebody who was not there.
 */
data class DiagnosticEntry(
    val at: Instant,
    val level: DiagnosticLevel,
    val area: DiagnosticArea,
    val event: String,
    val detail: String?,
    /**
     * The throwable's type, and deliberately not its message.
     *
     * `message` is exactly where the things this app must not write down live:
     * Ktor puts the request URL in one, a file failure puts the path in one, and
     * a path on the desktop contains the reader's own name. The type is the half
     * that helps and the half that is safe, and keeping only it means no call
     * site can opt back into the other.
     */
    val causeType: String?,
)
