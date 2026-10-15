package io.lukia.core.infrastructure.db

import io.lukia.core.infrastructure.shelve
import io.lukia.core.infrastructure.testDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the database does when a book leaves the library.
 *
 * `RemoveDocumentUseCase` asks for the position and the layout by name; this is
 * about only the first of its four writes landing - the app killed, the process
 * dead, the desktop build quit mid-operation. Before the key that left a
 * position and a layout for a book on no shelf: nothing lists them, nothing
 * sweeps them, nothing can reach them, and they stayed for the install's life.
 */
class RemovedBookCascadeTest {

    private val database = testDatabase().apply { shelve(LOCATION, OTHER) }

    @Test
    fun removingABookTakesItsPositionWithIt() {
        database.readingPositionQueries.put(location = LOCATION, pageIndex = 41, pageCount = 300)

        database.documentQueries.deleteByLocation(LOCATION)

        assertNull(database.readingPositionQueries.selectByLocation(LOCATION).executeAsOneOrNull())
    }

    @Test
    fun removingABookTakesItsLayoutWithIt() {
        database.readerLayoutQueries.put(location = LOCATION, layout = "Text")

        database.documentQueries.deleteByLocation(LOCATION)

        assertNull(database.readerLayoutQueries.selectByLocation(LOCATION).executeAsOneOrNull())
    }

    /** One book off the shelf is one book's state gone, not everybody's. */
    @Test
    fun theOtherBooksKeepTheirs() {
        database.readingPositionQueries.put(location = LOCATION, pageIndex = 41, pageCount = 300)
        database.readingPositionQueries.put(location = OTHER, pageIndex = 7, pageCount = 90)

        database.documentQueries.deleteByLocation(LOCATION)

        val kept = database.readingPositionQueries.selectAll().executeAsList()

        assertEquals(1, kept.size)
        assertEquals(OTHER, kept.single().location)
    }

    /**
     * The hours somebody spent reading are the one thing removing a book does
     * not take back - see `ReadingSession.sq`. A key here would have made that
     * a cascade, which is why there is not one.
     */
    @Test
    fun theSittingsSpentInItAreNotTakenBack() {
        database.readingSessionQueries.start(
            location = LOCATION,
            startedAt = 1_700_000_000_000L,
            uuid = "0b6f8c1e-6d7a-4c1b-9e1f-3a2b5c4d6e7f",
        )

        database.documentQueries.deleteByLocation(LOCATION)

        assertTrue(database.readingSessionQueries.selectAll().executeAsList().isNotEmpty())
    }

    /**
     * The other half of the key, and the one that has to be said out loud: with
     * it on, a position for a book no library holds is refused rather than
     * stored. Nothing in the app can ask for that - the reader cannot open a
     * book the catalogue does not know - but the rule is now the database's, and
     * a test that only proved the cascade would leave this half unstated.
     */
    @Test
    fun aPositionForABookNoLibraryHoldsIsRefused() {
        assertFailsWith<Exception> {
            database.readingPositionQueries.put(
                location = "/books/never-imported.epub",
                pageIndex = 1,
                pageCount = 10,
            )
        }
    }

    private companion object {
        const val LOCATION = "/books/moby.epub"
        const val OTHER = "/books/dune.pdf"
    }
}
