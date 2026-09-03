package io.lukia.core.infrastructure.diagnostics

/**
 * Android's own handler, kept and called after ours.
 *
 * Chaining is not a nicety here. The handler already installed is the one that
 * shows the "app keeps stopping" dialog and hands the crash to Play, and Play's
 * own reporting is what this app uses instead of shipping a crash SDK - so
 * replacing it would trade the reporting that works for a file on the device.
 */
internal actual fun installCrashHook(onCrash: (Throwable) -> Unit) {
    val previous = Thread.getDefaultUncaughtExceptionHandler()

    Thread.setDefaultUncaughtExceptionHandler { thread, failure ->
        // Swallowed on purpose: the process is already ending, and a failure
        // while writing a note about a failure must not replace the one worth
        // reading.
        runCatching { onCrash(failure) }

        previous?.uncaughtException(thread, failure)
    }
}
