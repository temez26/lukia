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
 * `LukiaDriverTest` asks the same of the desktop build, and its answer says
 * nothing about here: the drivers share settings and no code. The desktop passes
 * them as connection properties, iOS as a driver configuration, Android as
 * statements against the open connection - so Android is the one where opening
 * can fail on the way in, and it did. A `PRAGMA` that sets a value also answers
 * with it, and Android's `execSQL` refuses any statement returning a row; the
 * refusal arrived inside `onConfigure`, so every screen reading the library
 * showed "something went wrong" and no book could be opened.
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
