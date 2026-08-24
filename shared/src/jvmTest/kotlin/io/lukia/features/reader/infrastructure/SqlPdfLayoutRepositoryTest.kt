package io.lukia.features.reader.infrastructure

import io.lukia.core.infrastructure.testDatabase
import io.lukia.features.reader.domain.PdfLayout
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SqlPdfLayoutRepositoryTest {

    private val repository = SqlPdfLayoutRepository(
        database = testDatabase(),
        dispatcher = UnconfinedTestDispatcher(),
    )

    /** A book nobody has chosen for is read as the file laid itself out. */
    @Test
    fun aBookWithNoChoiceStoredOpensAsItsOwnPages() = runTest {
        assertEquals(PdfLayout.Original, repository.layoutFor("/books/one.pdf"))
    }

    @Test
    fun aChoiceComesBackForTheBookItWasMadeAbout() = runTest {
        repository.save("/books/one.pdf", PdfLayout.Text)

        assertEquals(PdfLayout.Text, repository.layoutFor("/books/one.pdf"))
        assertEquals(PdfLayout.Original, repository.layoutFor("/books/two.pdf"))
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

        assertEquals(PdfLayout.Original, repository.layoutFor("/books/one.pdf"))
    }
}
