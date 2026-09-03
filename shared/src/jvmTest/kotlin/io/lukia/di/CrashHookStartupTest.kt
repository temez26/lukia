package io.lukia.di

import app.cash.sqldelight.db.SqlDriver
import io.lukia.core.domain.diagnostics.CrashStore
import io.lukia.core.domain.diagnostics.FakeCrashStore
import io.lukia.core.infrastructure.lukiaSqlDriver
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotSame

/**
 * The graph installs the crash hook without anybody asking for it.
 *
 * Worth a test of its own because of how it fails if it is wrong: nothing
 * throws, nothing is missing, the app runs exactly as it does now - and the file
 * is simply never written, which nobody finds out until they go looking for a
 * crash report that was never there.
 *
 * Started for real rather than through `koinApplication`, since creating the
 * eager instances is what `startKoin` does and is precisely the thing under
 * test. Everything that would touch the reader's own files is replaced.
 */
class CrashHookStartupTest {

    private val databaseFile = File(
        System.getProperty("java.io.tmpdir"),
        "lukia-crash-hook-${kotlin.random.Random.nextInt(until = Int.MAX_VALUE)}.db",
    )

    private val originalHandler = Thread.getDefaultUncaughtExceptionHandler()

    @AfterTest
    fun tearDown() {
        stopKoin()
        Thread.setDefaultUncaughtExceptionHandler(originalHandler)
        databaseFile.delete()
    }

    @Test
    fun startingTheAppPutsTheCrashHookInPlace() {
        // A handler of this test's own rather than whatever the JVM happens to
        // hold: other tests in this suite install and restore handlers, and an
        // assertion against "whatever was there" would be about them.
        val sentinel = Thread.UncaughtExceptionHandler { _, _ -> }
        Thread.setDefaultUncaughtExceptionHandler(sentinel)

        startKoin {
            modules(
                commonModule,
                platformModule,
                module {
                    single<SqlDriver> { lukiaSqlDriver(databaseFile) }
                    // Never the real one: a test has no business writing into
                    // the directory the installed app keeps its state in.
                    single<CrashStore> { FakeCrashStore() }
                },
            )
        }

        assertNotSame(
            sentinel,
            Thread.getDefaultUncaughtExceptionHandler(),
            "the graph was built without installing the crash hook",
        )
    }
}
