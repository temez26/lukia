package io.lukia.features.library

import io.lukia.features.library.application.ImportSummary
import io.lukia.features.library.presentation.model.LibraryMessage
import io.lukia.features.library.presentation.model.notice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ImportNoticeTest {

    @Test
    fun aCleanImportSaysNothing() {
        assertNull(ImportSummary(imported = 3, alreadyInLibrary = 0, failed = 0).notice())
    }

    /**
     * One book and four are the same outcome with a different count. Whether
     * that count is spoken - "Already in your library" against "All 4 already
     * in your library" - is the plural resource's decision, not this one's.
     */
    @Test
    fun booksAlreadyHeldAreReportedByCount() {
        assertEquals(
            LibraryMessage.AlreadyInLibrary(1),
            ImportSummary(imported = 0, alreadyInLibrary = 1, failed = 0).notice(),
        )

        assertEquals(
            LibraryMessage.AlreadyInLibrary(4),
            ImportSummary(imported = 0, alreadyInLibrary = 4, failed = 0).notice(),
        )
    }

    /** The count that did arrive travels even when it is nought. */
    @Test
    fun aFailureAlwaysLeadsWithWhatDidArrive() {
        assertEquals(
            LibraryMessage.Imported(imported = 0, alreadyInLibrary = 0, failed = 1),
            ImportSummary(imported = 0, alreadyInLibrary = 0, failed = 1).notice(),
        )
    }

    @Test
    fun aMixedImportReportsEveryOutcome() {
        assertEquals(
            LibraryMessage.Imported(imported = 5, alreadyInLibrary = 2, failed = 1),
            ImportSummary(imported = 5, alreadyInLibrary = 2, failed = 1).notice(),
        )
    }
}
