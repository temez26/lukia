package io.lukia.features.profile

import io.lukia.features.library.FakeImportLibraryRepository
import io.lukia.features.library.book
import io.lukia.features.library.finished
import io.lukia.features.library.partRead
import io.lukia.features.profile.application.GetReadingSummaryUseCase
import io.lukia.features.reader.domain.FakeReadingProgressRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** That the profile's figures come from both stores, joined on location. */
class GetReadingSummaryUseCaseTest {

    @Test
    fun countsTheLibraryAgainstThePositionsStoredForIt() = runTest {
        val halfway = book("Halfway")
        val done = book("Done")
        val waiting = book("Waiting")

        val library = FakeImportLibraryRepository(
            listOf(halfway.document, done.document, waiting.document),
        )
        val positions = FakeReadingProgressRepository()
        positions.save(halfway.document.document.location, partRead())
        positions.save(done.document.document.location, finished())

        val summary = GetReadingSummaryUseCase(library, positions)()

        assertEquals(3, summary.stats.booksInLibrary)
        assertEquals(1, summary.stats.booksReading)
        assertEquals(1, summary.stats.booksFinished)
        assertEquals(1, summary.stats.booksUnread)
    }

    @Test
    fun anEmptyLibraryReportsNothingRatherThanFailing() = runTest {
        val summary = GetReadingSummaryUseCase(
            FakeImportLibraryRepository(),
            FakeReadingProgressRepository(),
        )()

        assertEquals(0, summary.stats.booksInLibrary)
        assertEquals(emptyList(), summary.recent)
    }
}
