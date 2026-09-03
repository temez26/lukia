package io.lukia.core.domain.diagnostics

/**
 * A crash file that lives in memory, for tests that must not touch the one the
 * installed app keeps.
 *
 * The real stores write into app storage - on the desktop that is the developer's
 * own Lukia directory, which a test run has no business creating or emptying.
 */
class FakeCrashStore(private var held: String? = null) : CrashStore {

    override fun write(report: String) {
        held = report
    }

    override fun read(): String? = held

    override fun discard() {
        held = null
    }
}
