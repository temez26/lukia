package io.lukia.core.infrastructure

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import io.lukia.core.infrastructure.db.LukiaDatabase
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How the Android build opens its database.
 *
 * `LukiaDriverTest` asks the same questions of the desktop build, and the answer
 * there says nothing about here: the two drivers share the settings and no code
 * at all. The desktop hands its over as connection properties, iOS as a driver
 * configuration, and Android as statements run against the open connection - so
 * this is the one of the three where opening the database can fail on the way
 * in, and it did. A `PRAGMA` that sets a value also answers with it, and
 * Android's `execSQL` refuses any statement that returns a row; the refusal
 * arrived inside `onConfigure`, so every screen that reads the library showed
 * "something went wrong" and no book could be opened at all.
 *
 * On the host: Robolectric runs the platform's real SQLite, which is what makes
 * these worth asking here rather than on a device.
 */
@RunWith(RobolectricTestRunner::class)
class AndroidLukiaDriverTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val opened = mutableListOf<SqlDriver>()

    @AfterTest
    fun tearDown() {
        opened.forEach { it.close() }
    }

    /**
     * The whole of the bug: nothing here is about a setting, only about whether
     * the first read of the library gets an answer.
     */
    @Test
    fun theLibraryCanBeRead() {
        val database = LukiaDatabase(open())

        assertTrue(database.documentQueries.selectLocations().executeAsList().isEmpty())
    }

    /**
     * Off by default in SQLite, and the whole of what makes `ON DELETE CASCADE`
     * more than a comment - see `ReadingPosition.sq`.
     */
    @Test
    fun foreignKeysAreOn() {
        assertEquals(1L, open().pragma("foreign_keys"))
    }

    /**
     * Every repository works on the IO dispatcher, which is many threads; without
     * this the second write to land simply fails.
     */
    @Test
    fun aConnectionWaitsForOneThatIsBusy() {
        assertEquals(BusyTimeoutMillis.toLong(), open().pragma("busy_timeout"))
    }

    private fun open(): SqlDriver = lukiaSqlDriver(context).also { opened += it }

    private fun SqlDriver.pragma(name: String): Long? = executeQuery(
        identifier = null,
        sql = "PRAGMA $name",
        mapper = { cursor: SqlCursor ->
            QueryResult.Value(if (cursor.next().value) cursor.getLong(0) else null)
        },
        parameters = 0,
    ).value
}
