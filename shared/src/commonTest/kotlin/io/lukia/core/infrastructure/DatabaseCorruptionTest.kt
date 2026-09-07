package io.lukia.core.infrastructure

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What counts as a database being unreadable, and - the half that matters more -
 * what does not.
 *
 * Answering yes here puts a reader's library aside and starts them an empty one.
 * That is the right thing to do to a file SQLite cannot read, because the app
 * would otherwise fail at every launch for ever with nothing the reader can do
 * about it; it is the wrong thing to do to a disk that was full for a moment or
 * a migration with a bug in it, both of which get better on their own and
 * neither of which should cost anybody their shelves.
 *
 * In `commonTest` because there is nothing platform-specific about it and there
 * are three platforms that need it to be right - see `LukiaDriver.jvm.kt` and
 * its two neighbours.
 */
class DatabaseCorruptionTest {

    @Test
    fun aFileThatIsNotADatabaseIsCorruption() {
        assertTrue(failure("[SQLITE_NOTADB] File opened that is not a database file").isDatabaseCorruption())
    }

    @Test
    fun anEncryptedFileIsCorruption() {
        assertTrue(failure("file is encrypted or is not a database").isDatabaseCorruption())
    }

    @Test
    fun aMalformedImageIsCorruption() {
        assertTrue(failure("database disk image is malformed").isDatabaseCorruption())
    }

    /** SQLite is not consistent about case across its bindings. */
    @Test
    fun theWordingIsMatchedWhateverItsCase() {
        assertTrue(failure("Database Disk Image Is Malformed").isDatabaseCorruption())
    }

    /**
     * Drivers wrap. What reaches the caller on two of the three platforms is the
     * driver's own exception with SQLite's underneath it.
     */
    @Test
    fun aWrappedFailureIsStillRead() {
        val wrapped = failure(
            "could not open the database",
            cause = failure("[SQLITE_CORRUPT] database disk image is malformed"),
        )

        assertTrue(wrapped.isDatabaseCorruption())
    }

    @Test
    fun aFullDiskIsNot() {
        assertFalse(failure("[SQLITE_FULL] database or disk is full").isDatabaseCorruption())
    }

    @Test
    fun aDirectoryThatCannotBeWrittenToIsNot() {
        assertFalse(failure("unable to open database file").isDatabaseCorruption())
    }

    /**
     * The one worth being most careful about. A migration that throws is a bug
     * in this app, and treating it as a broken file would mean shipping a bad
     * migration and silently emptying the library of everybody who took the
     * update - which is worse than the crash, because a crash gets reported.
     */
    @Test
    fun aMigrationThatThrewIsNot() {
        assertFalse(
            failure("[SQLITE_ERROR] SQL error (duplicate column name: metaTitle)")
                .isDatabaseCorruption(),
        )
    }

    @Test
    fun aFailureWithNothingToSayIsNot() {
        assertFalse(failure(message = null).isDatabaseCorruption())
    }

    /** A cause that points at itself must not be walked for ever. */
    @Test
    fun aCycleOfCausesEnds() {
        val looping = object : Exception("could not open the database") {
            override val cause: Throwable get() = this
        }

        assertFalse(looping.isDatabaseCorruption())
    }

    private fun failure(message: String?, cause: Throwable? = null) = Exception(message, cause)
}
