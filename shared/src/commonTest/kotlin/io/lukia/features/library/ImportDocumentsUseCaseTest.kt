package io.lukia.features.library

import io.lukia.core.domain.AppError
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.domain.document.DocumentType
import io.lukia.core.domain.document.PickedDocument
import io.lukia.core.domain.document.TestDocuments
import io.lukia.core.domain.toAppError
import io.lukia.features.library.application.ImportDocumentUseCase
import io.lukia.features.library.application.ImportDocumentsUseCase
import io.lukia.features.library.application.ImportSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ImportDocumentsUseCaseTest {

    @Test
    fun importsEveryDocumentItIsGiven() = runTest {
        val documents = listOf(
            TestDocuments.pdf(location = "/test/book.pdf"),
            TestDocuments.epub(location = "/test/novel.epub"),
        )

        val repository = FakeImportLibraryRepository()

        val summary = createUseCase(repository)(documents).getOrThrow()

        assertEquals(ImportSummary(imported = 2, alreadyInLibrary = 0, failed = 0), summary)
        assertEquals(documents, repository.documents.map { it.document })
    }

    @Test
    fun countsRejectedDocumentsWithoutAbortingTheBatch() = runTest {
        val documents = listOf(
            TestDocuments.pdf(location = "/test/book.pdf"),
            TestDocuments.unsupported(location = "/test/notes.mobi"),
            TestDocuments.epub(location = "/test/novel.epub"),
        )

        val repository = FakeImportLibraryRepository()

        val summary = createUseCase(repository)(documents).getOrThrow()

        assertEquals(ImportSummary(imported = 2, alreadyInLibrary = 0, failed = 1), summary)
        assertEquals(2, repository.documents.size)
    }

    @Test
    fun analyzerFailureIsCountedAndTheRemainingFilesStillImport() = runTest {
        val unreadable = TestDocuments.missingPdf(location = "/test/missing.pdf")
        val readable = TestDocuments.pdf(location = "/test/book.pdf")

        val repository = FakeImportLibraryRepository()

        val summary = createUseCase(
            repository = repository,
            failingLocations = setOf(unreadable.location),
        )(listOf(unreadable, readable)).getOrThrow()

        assertEquals(ImportSummary(imported = 1, alreadyInLibrary = 0, failed = 1), summary)
        assertEquals(listOf(readable), repository.documents.map { it.document })
    }

    /**
     * Rescanning a shelf after adding two books to it must cost two imports, not
     * five hundred - analysing a file means opening it and rendering its cover.
     */
    @Test
    fun booksAlreadyInTheLibraryAreLeftAloneRatherThanReanalysed() = runTest {
        val stored = TestDocuments.pdf(location = "/test/book.pdf")
        val fresh = TestDocuments.epub(location = "/test/novel.epub")

        val repository = FakeImportLibraryRepository(
            initialDocuments = listOf(
                DocumentInfo(
                    document = stored,
                    type = DocumentType.PDF,
                    exists = true,
                    sizeBytes = 1024L,
                ),
            ),
        )

        val analyzed = mutableListOf<String>()

        val summary = ImportDocumentsUseCase(
            importDocumentUseCase = ImportDocumentUseCase(
                documentAnalyzer = RecordingAnalyzer(analyzed),
                libraryRepository = repository,
                dispatcher = UnconfinedTestDispatcher(testScheduler),
            ),
            libraryRepository = repository,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )(listOf(stored, fresh)).getOrThrow()

        assertEquals(ImportSummary(imported = 1, alreadyInLibrary = 1, failed = 0), summary)
        assertEquals(listOf(fresh.location), analyzed)
    }

    /**
     * A tally of one explains nothing. The reader picked that exact file, so a
     * batch of one hands back why it did not come in.
     */
    @Test
    fun aSingleUnreadableFileFailsWithItsOwnReason() = runTest {
        val result = createUseCase(FakeImportLibraryRepository())(
            listOf(TestDocuments.unsupported(location = "/test/notes.mobi")),
        )

        assertEquals(
            AppError.UnsupportedFileType,
            result.exceptionOrNull()?.toAppError(),
        )
    }

    /**
     * A folder walked through a symlink back into itself hands the same path in
     * twice. Reading it twice is a second cover rendered to produce the row the
     * first one already stored, and it made one book arrive as two.
     */
    @Test
    fun theSameFileHandedInTwiceIsOneBook() = runTest {
        val book = TestDocuments.pdf(location = "/test/book.pdf")
        val repository = FakeImportLibraryRepository()
        val analyzed = mutableListOf<String>()

        val summary = ImportDocumentsUseCase(
            importDocumentUseCase = ImportDocumentUseCase(
                documentAnalyzer = RecordingAnalyzer(analyzed),
                libraryRepository = repository,
                dispatcher = UnconfinedTestDispatcher(testScheduler),
            ),
            libraryRepository = repository,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )(listOf(book, book, TestDocuments.epub(location = "/test/novel.epub"))).getOrThrow()

        assertEquals(ImportSummary(imported = 2, alreadyInLibrary = 0, failed = 0), summary)
        assertEquals(listOf(book.location, "/test/novel.epub"), analyzed)
    }

    /** The count a progress notice reads from is what is left after that, not what was handed in. */
    @Test
    fun repeatedFilesAreNotCountedInTheProgressTotal() = runTest {
        val book = TestDocuments.pdf(location = "/test/book.pdf")
        val seen = mutableListOf<Pair<Int, Int>>()

        createUseCase(FakeImportLibraryRepository())(
            listOf(book, book, TestDocuments.epub(location = "/test/novel.epub")),
        ) { completed, total -> seen += completed to total }

        assertEquals(listOf(1 to 2, 2 to 2), seen)
    }

    /**
     * A folder of any size is written in groups rather than a book at a time.
     *
     * The saving is the commits: a store makes each one durable before the next
     * may begin, so five thousand books used to mean five thousand of them. The
     * groups stay small on purpose - see `DocumentsPerTransaction` - because a
     * group is held in memory with every cover in it, and because a stopped
     * import keeps what has already arrived.
     */
    @Test
    fun aLargeImportIsWrittenInGroupsRatherThanABookAtATime() = runTest {
        val documents = List(60) { TestDocuments.pdf(location = "/test/book$it.pdf") }
        val repository = FakeImportLibraryRepository()

        val summary = createUseCase(repository)(documents).getOrThrow()

        assertEquals(ImportSummary(imported = 60, alreadyInLibrary = 0, failed = 0), summary)
        assertEquals(60, repository.documents.size)
        assertEquals(listOf(25, 25, 10), repository.batches, "written in groups of 25")
    }

    /**
     * Including the tail: the books left over when the last group is short still
     * reach the shelf rather than waiting for a group that never fills.
     */
    @Test
    fun anImportSmallerThanAGroupStillArrives() = runTest {
        val documents = List(3) { TestDocuments.pdf(location = "/test/book$it.pdf") }
        val repository = FakeImportLibraryRepository()

        createUseCase(repository)(documents).getOrThrow()

        assertEquals(3, repository.documents.size)
        assertEquals(listOf(3), repository.batches)
    }

    @Test
    fun reportsProgressAfterEachFile() = runTest {
        val seen = mutableListOf<Pair<Int, Int>>()

        createUseCase(FakeImportLibraryRepository())(
            listOf(
                TestDocuments.pdf(location = "/test/one.pdf"),
                TestDocuments.pdf(location = "/test/two.pdf"),
                TestDocuments.pdf(location = "/test/three.pdf"),
            ),
        ) { completed, total -> seen += completed to total }

        assertEquals(listOf(1 to 3, 2 to 3, 3 to 3), seen)
    }

    private fun TestScope.createUseCase(
        repository: FakeImportLibraryRepository,
        failingLocations: Set<String> = emptySet(),
    ) = ImportDocumentsUseCase(
        importDocumentUseCase = ImportDocumentUseCase(
            documentAnalyzer = TypeFromExtensionAnalyzer(failingLocations),
            libraryRepository = repository,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        ),
        libraryRepository = repository,
        dispatcher = UnconfinedTestDispatcher(testScheduler),
    )

    /** Records what it was asked to open, so a skipped file can be proven skipped. */
    private class RecordingAnalyzer(
        private val analyzed: MutableList<String>,
    ) : io.lukia.features.library.domain.DocumentAnalyzer {

        private val delegate = TypeFromExtensionAnalyzer()

        override suspend fun exists(
            document: io.lukia.core.domain.document.PickedDocument,
        ): Boolean = delegate.exists(document)

        override suspend fun analyze(document: io.lukia.core.domain.document.PickedDocument): DocumentInfo {
            analyzed += document.location
            return delegate.analyze(document)
        }
    }
}
