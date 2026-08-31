package io.lukia.features.reader.infrastructure

import io.lukia.core.domain.reading.PdfLayout
import io.lukia.core.infrastructure.testDatabase
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SqlPdfLayoutRepositoryTest {

    private val repository = SqlPdfLayoutRepository(
        database = testDatabase(),
        dispatcher = UnconfinedTestDispatcher(),
    )

    /**
     * A book nobody has chosen for answers with nothing rather than with a
     * layout, so the caller can tell it apart from a book chosen for and put its
     * own default in the gap.
     */
    @Test
    fun aBookWithNoChoiceStoredHasNoChoiceToGiveBack() = runTest {
        assertNull(repository.layoutFor("/books/one.pdf"))
    }

    @Test
    fun aChoiceComesBackForTheBookItWasMadeAbout() = runTest {
        repository.save("/books/one.pdf", PdfLayout.Text)

        assertEquals(PdfLayout.Text, repository.layoutFor("/books/one.pdf"))
        assertNull(repository.layoutFor("/books/two.pdf"))
    }

    @Test
    fun changingTheChoiceReplacesItRatherThanAddingASecondOne() = runTest {
        repository.save("/books/one.pdf", PdfLayout.Text)
        repository.save("/books/one.pdf", PdfLayout.Original)

        assertEquals(PdfLayout.Original, repository.layoutFor("/books/one.pdf"))
    }

    /** A book off the shelf takes its choice with it. */
    @Test
    fun forgettingABookForgetsHowItWasRead() = runTest {
        repository.save("/books/one.pdf", PdfLayout.Text)

        repository.forget("/books/one.pdf")

        assertNull(repository.layoutFor("/books/one.pdf"))
    }
}
