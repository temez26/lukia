package io.lukia.features.library

import io.lukia.features.library.application.ImportSummary
import io.lukia.features.library.presentation.notice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ImportNoticeTest {

    @Test
    fun aCleanImportSaysNothing() {
        assertNull(ImportSummary(imported = 3, alreadyInLibrary = 0, failed = 0).notice())
    }

    @Test
    fun oneBookAlreadyHeldSaysSoWithoutACount() {
        assertEquals(
            "Already in your library",
            ImportSummary(imported = 0, alreadyInLibrary = 1, failed = 0).notice(),
        )
    }

    @Test
    fun severalBooksAlreadyHeldAreCounted() {
        assertEquals(
            "All 4 already in your library",
            ImportSummary(imported = 0, alreadyInLibrary = 4, failed = 0).notice(),
        )
    }

    /** "0 imported" is the sentence that explains an unchanged library. */
    @Test
    fun aFailureAlwaysLeadsWithWhatDidArrive() {
        assertEquals(
            "Imported 0, 1 skipped",
            ImportSummary(imported = 0, alreadyInLibrary = 0, failed = 1).notice(),
        )
    }

    @Test
    fun aMixedImportReportsEveryOutcome() {
        assertEquals(
            "Imported 5, 2 already added, 1 skipped",
            ImportSummary(imported = 5, alreadyInLibrary = 2, failed = 1).notice(),
        )
    }
}
