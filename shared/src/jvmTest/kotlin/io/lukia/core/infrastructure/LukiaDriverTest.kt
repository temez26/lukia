package io.lukia.core.infrastructure

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import io.lukia.core.domain.diagnostics.DiagnosticLevel
import io.lukia.core.infrastructure.db.LukiaDatabase
import io.lukia.core.infrastructure.diagnostics.DiagnosticBuffer
import io.lukia.core.infrastructure.diagnostics.RecordingDiagnostics
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * How the desktop build opens its database, which is the half of this app's
 * storage that no query can prove.
 *
 * Every one of these is about a setting or a recovery that is invisible until
 * the day it matters: a cascade that does not fire, a second write that fails
 * because the first had the file, a library that will not open and cannot be
 * made to.
 */
class LukiaDriverTest {

    private val directory = File.createTempFile("lukia-driver", "").let {
        it.delete()
        it.mkdirs()
        it
    }

    private val file = File(directory, "lukia.db")
    private val buffer = DiagnosticBuffer()
    private val diagnostics = RecordingDiagnostics(buffer)
    private val opened = mutableListOf<SqlDriver>()

    @AfterTest
    fun tearDown() {
        opened.forEach { it.close() }
        directory.deleteRecursively()
    }

    /**
     * Off by default in SQLite, and the whole of what makes `ON DELETE CASCADE`
     * more than a comment - see `ReadingPosition.sq`.
     */
    @Test
    fun foreignKeysAreOn() {
        assertEquals(1L, open().pragma("foreign_keys"))
    }

    /** So that the shelf can be read while the sitting behind it is written. */
    @Test
    fun theJournalIsWriteAhead() {
        assertEquals("wal", open().textPragma("journal_mode").lowercase())
    }

    /**
     * Every repository works on the IO dispatcher, which is many threads and so
     * many connections; without this the second write to land simply fails.
     *
     * Asserted on a second connection rather than the first, because that is the
     * claim: this driver opens one per thread, so a setting that held only for
     * whichever was first would be the bug rather than the fix.
     */
    @Test
    fun everyConnectionWaitsForOneThatIsBusy() {
        val driver = open()

        val onAnotherThread = mutableListOf<Long?>()
        Thread { onAnotherThread += driver.pragma("busy_timeout") }.apply {
            start()
            join()
        }

        assertEquals(BusyTimeoutMillis.toLong(), onAnotherThread.single())
    }

    /** The ordinary case: a database that is there and readable is opened. */
    @Test
    fun anExistingLibraryIsOpenedRatherThanReplaced() {
        LukiaDatabase(open()).also { it.shelve("/books/moby.epub") }
        opened.removeLast().close()

        val reopened = LukiaDatabase(open())

        assertEquals(1, reopened.documentQueries.selectLocations().executeAsList().size)
    }

    /**
     * A file that is not a database is put aside and a new one started, rather
     * than failing every launch for as long as it is there. Nothing else in this
     * app can recover from that: the database is opened before there is a screen
     * to explain anything on.
     */
    @Test
    fun aFileThatIsNotADatabaseIsPutAsideAndTheAppStarts() {
        file.writeText("this is not a database")

        val database = LukiaDatabase(open())

        assertTrue(database.documentQueries.selectLocations().executeAsList().isEmpty())
        assertTrue(
            File(directory, "lukia.db.unreadable").exists(),
            "the unreadable file was not kept, so nothing could be recovered from it",
        )
    }

    /** And the reader's app knows why their library is empty. */
    @Test
    fun aFileThatIsNotADatabaseIsRecorded() {
        file.writeText("this is not a database")
        open()

        val recorded = buffer.entries.value.single { it.event == "database-unreadable" }

        assertEquals(DiagnosticLevel.Failure, recorded.level)
    }

    /**
     * Nothing of the old database is left where the new one will read it.
     *
     * A write-ahead log belongs to the database it was written beside, and one
     * left next to a new database of the same name is pages that database has
     * never seen, which SQLite would recover into it. Asserted as the outcome
     * rather than as a rename, because two things arrive at it: SQLite removes a
     * log it cannot use when it opens the file, and what it leaves behind is put
     * aside with the database - and which of the two did it is not the point.
     */
    @Test
    fun noneOfTheOldDatabaseIsLeftBesideTheNewOne() {
        file.writeText("this is not a database")
        File(directory, "lukia.db-wal").writeText(StaleLog)

        open()

        val leftBehind = directory.listFiles().orEmpty()
            .filter { !it.name.endsWith(QuarantineSuffix) }
            .filter { it.readText() == StaleLog }

        assertTrue(
            leftBehind.isEmpty(),
            "the new database was left holding the old one's log: $leftBehind",
        )
    }

    /**
     * And nothing else is treated that way. A database that will not open for
     * any other reason - a directory that cannot be written to, a migration that
     * threw - is a failure to report, not a library to throw away.
     */
    @Test
    fun aFailureThatIsNotCorruptionKeepsTheLibrary() {
        directory.deleteRecursively()
        directory.writeText("a file where the directory should be")

        assertFailsWith<Exception> { lukiaSqlDriver(File(directory, "lukia.db"), diagnostics) }

        assertTrue(
            buffer.entries.value.none { it.event == "database-unreadable" },
            "a failure that was not corruption was treated as corruption",
        )
    }

    private fun open(): SqlDriver = lukiaSqlDriver(file, diagnostics).also { opened += it }

    private fun SqlDriver.pragma(name: String): Long? = executeQuery(
        identifier = null,
        sql = "PRAGMA $name",
        mapper = { cursor: SqlCursor ->
            QueryResult.Value(if (cursor.next().value) cursor.getLong(0) else null)
        },
        parameters = 0,
    ).value

    private fun SqlDriver.textPragma(name: String): String = executeQuery(
        identifier = null,
        sql = "PRAGMA $name",
        mapper = { cursor: SqlCursor ->
            QueryResult.Value(if (cursor.next().value) cursor.getString(0).orEmpty() else "")
        },
        parameters = 0,
    ).value

    private companion object {
        const val StaleLog = "a log from the database that is no longer there"
        const val QuarantineSuffix = ".unreadable"
    }
}
