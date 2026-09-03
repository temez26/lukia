package io.lukia.core.infrastructure.diagnostics

/**
 * The JVM's own last-resort handler, kept and called after ours.
 *
 * Chained rather than replaced: the handler already installed is what prints the
 * trace and ends the process, and a desktop build that swallowed that would die
 * silently with no window and nothing on the console.
 */
internal actual fun installCrashHook(onCrash: (Throwable) -> Unit) {
    val previous = Thread.getDefaultUncaughtExceptionHandler()

    Thread.setDefaultUncaughtExceptionHandler { thread, failure ->
        // Swallowed on purpose, and the only place in this app where that is
        // right: the process is already ending, and a failure while writing a
        // note about a failure must not replace the one worth reading.
        runCatching { onCrash(failure) }

        previous?.uncaughtException(thread, failure)
    }
}
