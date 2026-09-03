package io.lukia.core.infrastructure.diagnostics

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.setUnhandledExceptionHook

/**
 * Kotlin/Native's own hook, which is the only one that sees a Kotlin failure.
 *
 * `NSSetUncaughtExceptionHandler` is the obvious thing to reach for here and is
 * the wrong one: it catches Objective-C exceptions, and a Kotlin exception
 * reaching the top of a Kotlin coroutine is not one. It would install cleanly
 * and then never fire.
 *
 * After the hook returns, the runtime ends the process - so whatever is done
 * here has to be done by then, which is why it writes one small file and nothing
 * else.
 */
@OptIn(ExperimentalNativeApi::class)
internal actual fun installCrashHook(onCrash: (Throwable) -> Unit) {
    // Installing is the only way to learn what was installed before, so this
    // puts a hook that does nothing in place to read the previous one back, then
    // replaces it with the one that calls both in the platform's own order.
    val previous = setUnhandledExceptionHook { }

    setUnhandledExceptionHook { failure ->
        // Swallowed on purpose: the process is already ending, and a failure
        // while writing a note about a failure must not replace the one worth
        // reading.
        runCatching { onCrash(failure) }

        previous?.invoke(failure)
    }
}
