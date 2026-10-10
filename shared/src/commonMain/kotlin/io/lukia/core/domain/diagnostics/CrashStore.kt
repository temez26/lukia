package io.lukia.core.domain.diagnostics

/**
 * The one file the app leaves behind when it dies, so that the next run has
 * something to show for it.
 *
 * One file, overwritten: a folder of them would grow on a reader's device
 * without anybody deciding it should, and the crash worth reading is the one
 * that just happened.
 *
 * App-private on every platform, and nothing here can send it anywhere. Written
 * as the process dies, read once on the next run, deleted on request.
 */
interface CrashStore {

    /** Replaces whatever was there. Called while the app is dying, so it must be quick. */
    fun write(report: String)

    /** What the last death left, or null when the app closed the way it was meant to. */
    fun read(): String?

    fun discard()
}
