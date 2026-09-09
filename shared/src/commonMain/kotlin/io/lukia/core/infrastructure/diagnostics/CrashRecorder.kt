package io.lukia.core.infrastructure.diagnostics

import io.lukia.core.domain.diagnostics.CrashStore
import io.lukia.getPlatform
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Leaves a note behind when the app dies, and hands it to the reader next time
 * they open it.
 *
 * The rest of the record is an in-memory buffer, so the one failure that takes
 * the app down is the one it can never show anybody. Only a file survives.
 *
 * The consent split is deliberate: the stack trace is written whether or not
 * diagnostics are on - app-private, never sent, the reader's to delete - because
 * a crash report is worth most from the reader who turned nothing on. The run-up
 * is not: [buffer] is empty unless diagnostics are on, so the breadcrumbs follow
 * the setting with no code deciding that they should.
 *
 * No third-party crash reporting sits behind this and none is planned. Apple and
 * Play already collect crashes under consent given to the platform.
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
