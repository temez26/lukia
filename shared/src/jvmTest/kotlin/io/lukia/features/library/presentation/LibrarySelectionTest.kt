package io.lukia.features.library.presentation

import io.lukia.MainDispatcherTest
import io.lukia.core.domain.BookMetadata
import io.lukia.core.domain.DocumentInfo
import io.lukia.core.domain.FakeReadingProgressRepository
import io.lukia.core.domain.ReadingProgress
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Picking several books at once, and doing one thing to all of them.
 */
class LibrarySelectionTest : MainDispatcherTest() {

    private val one = pdfDocumentInfo(location = "/test/one.pdf")
    private val two = pdfDocumentInfo(location = "/test/two.pdf")
    private val three = pdfDocumentInfo(location = "/test/three.pdf")

    private fun shelf(vararg books: DocumentInfo) =
        FakeLibraryRepository(initialDocuments = books.toList())

    @Test
    fun theShelfStartsOutNotBeingSelectedFrom() = runTest {
        val viewModel = createViewModel(shelf(one))

        assertFalse(viewModel.selection.value.active)
    }

    /**
     * A shelf somebody has just started selecting from is still not the shelf
     * they were browsing, even with nothing ticked yet.
     */
    @Test
    fun selectingStartsWithNothingTicked() = runTest {
        val viewModel = createViewModel(shelf(one, two))

        viewModel.startSelecting()

        assertTrue(viewModel.selection.value.active)
        assertTrue(viewModel.selection.value.isEmpty)
    }

    @Test
    fun aBookIsTickedAndUntickedByTheSameAction() = runTest {
        val viewModel = createViewModel(shelf(one, two))
        viewModel.startSelecting()

        viewModel.toggleSelected(one.document.location)
        assertEquals(setOf(one.document.location), viewModel.selection.value.locations)

        viewModel.toggleSelected(one.document.location)
        assertTrue(viewModel.selection.value.isEmpty)
    }

    @Test
    fun leavingTheModeForgetsWhatWasTicked() = runTest {
        val viewModel = createViewModel(shelf(one, two))
        viewModel.startSelecting()
        viewModel.toggleSelected(one.document.location)

        viewModel.stopSelecting()

        assertFalse(viewModel.selection.value.active)
        assertTrue(viewModel.selection.value.isEmpty)
    }

    /** One control, because a shelf is either all picked or it is not. */
    @Test
    fun selectAllTicksEverythingAndThenClearsIt() = runTest {
        val viewModel = createViewModel(shelf(one, two, three))
        viewModel.startSelecting()

        viewModel.toggleAllSelected()
        assertEquals(3, viewModel.selection.value.count)

        viewModel.toggleAllSelected()
        assertTrue(viewModel.selection.value.isEmpty)
    }

    /**
     * A reader who has narrowed to one author and pressed Select All has
     * selected that author, not everything they own.
     */
    @Test
    fun selectAllTakesWhatTheShelfIsShowingRatherThanTheWholeLibrary() = runTest {
        val whale = named(one, "Moby-Dick")
        val viewModel = createViewModel(shelf(whale, named(two, "Ulysses")))
        viewModel.updateQuery(LibraryQuery(search = "moby"))
        viewModel.startSelecting()

        viewModel.toggleAllSelected()

        assertEquals(setOf(whale.document.location), viewModel.selection.value.locations)
    }

    /** The fixture's books all share a file name, so a search needs a real title. */
    private fun named(book: DocumentInfo, title: String) =
        book.copy(metadata = BookMetadata(title = title))

    @Test
    fun deletingTheSelectionTakesThoseBooksOffTheShelf() = runTest {
        val repository = shelf(one, two, three)
        val viewModel = createViewModel(repository)
        viewModel.startSelecting()
        viewModel.toggleSelected(one.document.location)
        viewModel.toggleSelected(three.document.location)

        viewModel.deleteSelected()

        assertEquals(listOf(two.document.location), repository.documents.map { it.document.location })
    }

    /** The mode is over once it has been acted on: there is nothing left ticked. */
    @Test
    fun anActionLeavesSelectionModeBehindIt() = runTest {
        val viewModel = createViewModel(shelf(one, two))
        viewModel.startSelecting()
        viewModel.toggleSelected(one.document.location)

        viewModel.deleteSelected()

        assertFalse(viewModel.selection.value.active)
    }

    @Test
    fun deletingReportsWhatItDid() = runTest {
        val viewModel = createViewModel(shelf(one, two))
        viewModel.startSelecting()
        viewModel.toggleSelected(one.document.location)

        viewModel.deleteSelected()

        assertEquals("1 book removed from your library", viewModel.message.value)
    }

    @Test
    fun favouritingTheSelectionMarksEveryBookInIt() = runTest {
        val repository = shelf(one, two, three)
        val viewModel = createViewModel(repository)
        viewModel.startSelecting()
        viewModel.toggleSelected(one.document.location)
        viewModel.toggleSelected(two.document.location)

        viewModel.setFavoriteOnSelected()

        assertEquals(
            mapOf(
                one.document.location to true,
                two.document.location to true,
                three.document.location to false,
            ),
            repository.documents.associate { it.document.location to it.isFavorite },
        )
    }

    /** A shelf already favourited is how a reader takes it back off again. */
    @Test
    fun favouritingASelectionThatAlreadyIsClearsIt() = runTest {
        val favourited = pdfDocumentInfo(location = "/test/one.pdf", favorite = true)
        val repository = shelf(favourited)
        val viewModel = createViewModel(repository)
        viewModel.startSelecting()
        viewModel.toggleSelected(favourited.document.location)

        viewModel.setFavoriteOnSelected()

        assertFalse(repository.documents.single().isFavorite)
    }

    @Test
    fun resettingProgressPutsTheBooksBackToUnread() = runTest {
        val progress = FakeReadingProgressRepository()
        progress.save(one.document.location, ReadingProgress(pageIndex = 40, pageCount = 100))

        val viewModel = createViewModel(shelf(one, two), readingProgress = progress)
        viewModel.startSelecting()
        viewModel.toggleSelected(one.document.location)

        viewModel.resetProgressOnSelected()

        assertEquals(null, progress.progressFor(one.document.location))
    }

    /** Nothing ticked is nothing to do, and no report about having done it. */
    @Test
    fun anActionOnAnEmptySelectionDoesNothing() = runTest {
        val repository = shelf(one, two)
        val viewModel = createViewModel(repository)
        viewModel.startSelecting()

        viewModel.deleteSelected()

        assertEquals(2, repository.documents.size)
        assertTrue(viewModel.selection.value.active)
        assertEquals(null, viewModel.message.value)
    }

    /**
     * The upkeep actions end by ticking what they found and leaving the reader
     * looking at it, rather than at a report about it.
     */
    @Test
    fun findingMissingFilesTicksThemOnTheShelf() = runTest {
        val gone = one.copy(exists = false)
        val viewModel = createViewModel(shelf(gone, two))

        viewModel.selectMissing()

        assertTrue(viewModel.selection.value.active)
        assertEquals(setOf(gone.document.location), viewModel.selection.value.locations)
    }

    @Test
    fun findingDuplicatesTicksEveryCopyButTheFirst() = runTest {
        val original = named(one, "Twice")
        val copy = named(two, "Twice")
        val viewModel = createViewModel(shelf(original, copy))

        viewModel.selectDuplicates()

        assertEquals(setOf(copy.document.location), viewModel.selection.value.locations)
    }

    /** And then the shelf is tidied by the delete that was already there. */
    @Test
    fun theDuplicatesCanBeDeletedLeavingOneOfEachBook() = runTest {
        val repository = shelf(named(one, "Twice"), named(two, "Twice"), three)
        val viewModel = createViewModel(repository)

        viewModel.selectDuplicates()
        viewModel.deleteSelected()

        assertEquals(
            listOf(one.document.location, three.document.location),
            repository.documents.map { it.document.location },
        )
    }
}
