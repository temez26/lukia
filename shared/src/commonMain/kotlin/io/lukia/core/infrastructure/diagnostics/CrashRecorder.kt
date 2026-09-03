package io.lukia.core.infrastructure.diagnostics

import io.lukia.core.domain.diagnostics.CrashStore
import io.lukia.getPlatform
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Leaves a note behind when the app dies, and hands it to the reader next time
 * they open it.
 *
 * This exists because of what the rest of the record cannot do: the buffer is in
 * memory, so the one failure that takes the app down is the one failure it can
 * never show anybody. A file is the only thing that survives the process.
 *
 * The trade it makes is deliberate and worth stating. The stack trace is written
 * whether or not diagnostics are on, because a crash report is worth most from
 * the reader who never turned anything on - and writing it costs no more consent
 * than a preferences file does, since it is app-private, never sent, and theirs
 * to delete. What is *not* written unasked is the run-up: [buffer] is empty
 * unless the reader turned diagnostics on, so the breadcrumbs follow the setting
 * without a line of code deciding that they should.
 *
 * No third-party crash reporting sits behind this and none is planned. Apple and
 * Play already collect crashes under consent the reader gave the platform, and
 * an SDK that phoned home from a reader whose library never leaves the device
 * would be a larger change than everything this file is for.
 */
class CrashRecorder(
    private val store: CrashStore,
    private val buffer: DiagnosticBuffer,
    private val platformName: String = getPlatform().name,
    private val now: () -> Instant = { Clock.System.now() },
) {

    /**
     * Called once, as early in the app's life as the graph is built.
     *
     * Installing twice would write the same note twice; the graph holds one of
     * these, which is what keeps that from happening.
     */
    fun install() = installCrashHook { failure ->
        store.write(
            crashReport(
                failure = failure,
                entries = buffer.entries.value,
                platformName = platformName,
                at = now(),
            ),
        )
    }

    /** What the last death left, or null when the app closed the way it was meant to. */
    fun lastCrash(): String? = store.read()

    fun discard() = store.discard()
}
