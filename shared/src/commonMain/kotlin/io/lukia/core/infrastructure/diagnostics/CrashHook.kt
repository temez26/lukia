package io.lukia.core.infrastructure.diagnostics

/**
 * Hands [onCrash] the failure that is about to end the process, then lets it end
 * exactly as it would have.
 *
 * Every implementation chains to whatever was installed before it. That is the
 * whole of the contract and the easy half to get wrong: replacing the platform's
 * own handler would take away Android's crash dialog and the reports Play and
 * Apple already collect - which are the crash reporting this app deliberately
 * relies on instead of shipping an SDK of its own. This adds a file beside them;
 * it must not stand in for them.
 *
 * [onCrash] runs on a dying process and must not throw, block or allocate much.
 */
internal expect fun installCrashHook(onCrash: (Throwable) -> Unit)
