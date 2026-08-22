package io.lukia.core.infrastructure.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.lukia.core.domain.CoverImage
import io.lukia.core.domain.DocumentInfo
import io.lukia.core.domain.DocumentType
import io.lukia.core.domain.PickedDocument
import io.lukia.features.library.infrastructure.SqlLibraryRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A library stored before covers were held to a size has to be readable by the
 * version that holds them to one.
 *
 * This migration adds no columns - it clears the cover art that was already too
 * large to read back on Android, which is what a database written by an earlier
 * version can be full of. The table it runs against is the one already here, so
 * the schema is created as it stands, filled as an earlier version would have
 * filled it, and only the migration itself is run.
 */
class CoverSizeMigrationTest {

    /** The last version that stored a cover at whatever size it was found. */
    private val versionBeforeTheLimit = 4L

    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        .also { LukiaDatabase.Schema.create(it) }

    private val repository = SqlLibraryRepository(LukiaDatabase(driver))

    @Test
    fun aCoverTooLargeToReadBackIsCleared() = runTest {
        repository.addDocument(book("/books/huge-cover.epub", coverBytes = 2_400_000))

        migrateFromTheversionBeforeTheLimit()

        assertNull(repository.getDocument("/books/huge-cover.epub")?.coverImage)
    }

    @Test
    fun aCoverTheLibraryCanCarryIsLeftAlone() = runTest {
        repository.addDocument(book("/books/ordinary.epub", coverBytes = 120_000))

        migrateFromTheversionBeforeTheLimit()

        val stored = repository.getDocument("/books/ordinary.epub")

        assertNotNull(stored?.coverImage)
        assertEquals(120_000, stored.coverImage?.bytes?.size)
        // The book itself is untouched: this clears artwork, not shelves.
        assertEquals("ordinary.epub", stored.document.fileName)
    }

    @Test
    fun theSchemaIsPastTheVersionThatStoredCoversUnmeasuredSoTheMigrationIsReachable() {
        assertTrue(
            LukiaDatabase.Schema.version > versionBeforeTheLimit,
            "Clearing oversized covers needs a schema version bump, or no " +
                "installed database will ever have it run against it.",
        )
    }

    /**
     * Only the one step this is about. The schema above was created as it
     * stands today, so running every later migration over it would replay
     * statements whose tables are already there - and each one of those is a
     * migration with a test of its own.
     */
    private fun migrateFromTheversionBeforeTheLimit() {
        LukiaDatabase.Schema.migrate(
            driver,
            oldVersion = versionBeforeTheLimit,
            newVersion = versionBeforeTheLimit + 1,
        )
    }

    private fun book(location: String, coverBytes: Int) = DocumentInfo(
        document = PickedDocument(
            fileName = location.substringAfterLast('/'),
            extension = "epub",
            location = location,
        ),
        type = DocumentType.EPUB,
        exists = true,
        sizeBytes = 2048,
        coverImage = CoverImage(ByteArray(coverBytes)),
    )
}
