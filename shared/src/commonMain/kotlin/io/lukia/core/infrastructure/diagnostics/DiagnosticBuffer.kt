package io.lukia.core.infrastructure.diagnostics

import io.lukia.core.domain.diagnostics.DiagnosticEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * How many entries are kept before the oldest is dropped.
 *
 * The way these get read is: turn diagnostics on, do the thing that went wrong,
 * hand over what was recorded. One reproduction is a handful of entries, so this
 * is not a working size - it is a ceiling for the pathological document, and the
 * rule that boundaries record once per document rather than once per page is
 * what keeps an ordinary session nowhere near it.
 *
 * Two hundred short entries is tens of kilobytes.
 */
private const val DefaultCapacity = 200

/**
 * The entries this run of the app has collected, held in memory and nowhere else.
 *
 * Nothing is written to disk. A log file outlives the session that made it, gets
 * swept up by a backup, and turns up in a file manager months later - and for an
 * app whose whole proposition is that a library never leaves the device, the
 * cheapest thing to promise about a diagnostic record is that it dies with the
 * process.
 */
class DiagnosticBuffer(private val capacity: Int = DefaultCapacity) {

    private val _entries = MutableStateFlow<List<DiagnosticEntry>>(emptyList())

    /** Oldest first, which is the order they are read back in. */
    val entries: StateFlow<List<DiagnosticEntry>> = _entries.asStateFlow()

    /**
     * Appended from wherever the failure happened - a parser thread, an HTTP
     * response, a migration - and read from a screen.
     *
     * [MutableStateFlow.update] is a compare-and-set loop, which is what makes
     * that safe without a lock, without an `expect`/`actual` for one, and
     * without a dependency: the coroutines library is already here. Copying the
     * list on every append costs nothing at the rate these arrive, and is what
     * lets a reader collect the flow without the list changing underneath it.
     */
    fun add(entry: DiagnosticEntry) = _entries.update { held ->
        (if (held.size >= capacity) held.drop(held.size - capacity + 1) else held) + entry
    }

    fun clear() = _entries.update { emptyList() }
}
