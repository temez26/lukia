package io.lukia.features.reader.presentation

import io.lukia.core.domain.AppError
import io.lukia.core.domain.DocumentInfo
import io.lukia.core.domain.DocumentType
import io.lukia.core.domain.PickedDocument
import io.lukia.core.infrastructure.JvmDocumentWorkspace
import io.lukia.core.presentation.UiState
import io.lukia.core.presentation.dataOrNull
import io.lukia.features.library.domain.LibraryRepository
import io.lukia.features.reader.application.OpenDocumentUseCase
import io.lukia.features.reader.domain.ReaderDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.common.PDRectangle
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {

    private val root = File(
        System.getProperty("java.io.tmpdir"),
        "lukia-reader-test-${kotlin.random.Random.nextInt(until = Int.MAX_VALUE)}"
    )
    private val library = File(root, "library").also { it.mkdirs() }
    private val workspace = JvmDocumentWorkspace(directory = File(root, "workspace"))

    /**
     * Opening a document reads a real file and parses a real PDF. There is no
     * virtual time to advance, and the copy step hands off to [Dispatchers.IO]
     * on the way - so these tests wait for the state to settle through
     * [settled] rather than pretending the work is synchronous.
     */
    private val openDispatcher = Dispatchers.Unconfined

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        root.deleteRecursively()
    }

    @Test
    fun opensTheDocumentOnInit() = runTest {
        val document = pdfInLibrary(pageCount = 5)
        val viewModel = createViewModel(document)

        val opened = viewModel.settled().dataOrNull

        assertNotNull(opened)
        assertEquals(5, opened.pages.pageCount)
    }

    /** Reading has to happen against the copy, never the imported file. */
    @Test
    fun readsFromTheWorkingCopyRatherThanTheOriginal() = runTest {
        val document = pdfInLibrary(pageCount = 1)
        val viewModel = createViewModel(document)

        val opened = assertNotNull(viewModel.settled().dataOrNull)

        assertEquals(document.document.location, opened.workingCopy.sourceLocation)
        assertTrue(opened.workingCopy.path != document.document.location)
        assertTrue(File(opened.workingCopy.path).isFile)
    }

    @Test
    fun aLocationWithNoMatchBecomesDocumentNotFound() = runTest {
        val viewModel = ReaderViewModel(
            location = "/library/missing.pdf",
            openDocumentUseCase = OpenDocumentUseCase(
                libraryRepository = FakeLibraryRepository(),
                documentWorkspace = workspace,
                dispatcher = openDispatcher
            )
        )

        assertEquals(UiState.Failure(AppError.DocumentNotFound), viewModel.settled())
    }

    /**
     * An EPUB is a book the library legitimately holds and the reader cannot
     * open yet, which is not the same as a file the app never accepted.
     */
    @Test
    fun aFormatTheReaderCannotOpenYetSaysSo() = runTest {
        val epub = File(library, "book.epub").apply { writeText("not read yet") }
        val document = DocumentInfo(
            document = PickedDocument(
                fileName = epub.name,
                extension = "epub",
                location = epub.absolutePath
            ),
            type = DocumentType.EPUB,
            exists = true,
            sizeBytes = epub.length()
        )

        val viewModel = createViewModel(document)

        assertEquals(UiState.Failure(AppError.FormatNotReadable), viewModel.settled())
    }

    @Test
    fun aFileThatCannotBeOpenedBecomesAFailure() = runTest {
        val broken = File(library, "broken.pdf").apply { writeText("not a pdf") }
        val document = DocumentInfo(
            document = PickedDocument(
                fileName = broken.name,
                extension = "pdf",
                location = broken.absolutePath
            ),
            type = DocumentType.PDF,
            exists = true,
            sizeBytes = broken.length()
        )

        val viewModel = createViewModel(document)

        assertTrue(viewModel.settled() is UiState.Failure)
    }

    @Test
    fun retryReopensTheDocument() = runTest {
        val document = pdfInLibrary(pageCount = 2)
        val repository = FakeLibraryRepository()
        val viewModel = ReaderViewModel(
            location = document.document.location,
            openDocumentUseCase = OpenDocumentUseCase(
                libraryRepository = repository,
                documentWorkspace = workspace,
                dispatcher = openDispatcher
            )
        )

        assertEquals(UiState.Failure(AppError.DocumentNotFound), viewModel.settled())

        repository.addDocument(document)
        viewModel.retry()

        assertEquals(2, viewModel.settled().dataOrNull?.pages?.pageCount)
    }

    @Test
    fun rendersAPageAtTheWidthItIsAskedFor() = runTest {
        val viewModel = createViewModel(pdfInLibrary(pageCount = 1))
        viewModel.settled()

        val page = viewModel.page(index = 0, widthPx = 400)

        assertNotNull(page)
        assertEquals(400, page.width)
    }

    /** Scrolling back to a page must not redraw it. */
    @Test
    fun aPageAlreadyDrawnIsServedFromTheCache() = runTest {
        val viewModel = createViewModel(pdfInLibrary(pageCount = 1))
        viewModel.settled()

        val first = viewModel.page(index = 0, widthPx = 400)
        val second = viewModel.page(index = 0, widthPx = 400)

        assertTrue(first === second)
    }

    /** A resized window invalidates every page drawn for the old one. */
    @Test
    fun aPageIsRedrawnWhenTheWidthChanges() = runTest {
        val viewModel = createViewModel(pdfInLibrary(pageCount = 1))
        viewModel.settled()

        val narrow = viewModel.page(index = 0, widthPx = 400)
        val wide = viewModel.page(index = 0, widthPx = 700)

        assertEquals(400, narrow?.width)
        assertEquals(700, wide?.width)
    }

    @Test
    fun anUnopenedDocumentHasNoPagesToDraw() = runTest {
        val viewModel = ReaderViewModel(
            location = "/library/missing.pdf",
            openDocumentUseCase = OpenDocumentUseCase(
                libraryRepository = FakeLibraryRepository(),
                documentWorkspace = workspace,
                dispatcher = openDispatcher
            )
        )

        assertNull(viewModel.page(index = 0, widthPx = 400))
    }

    /** The state once the document has finished opening, one way or the other. */
    private suspend fun ReaderViewModel.settled(): UiState<ReaderDocument> =
        uiState.first { it !is UiState.Loading }

    private fun createViewModel(document: DocumentInfo) = ReaderViewModel(
        location = document.document.location,
        openDocumentUseCase = OpenDocumentUseCase(
            libraryRepository = FakeLibraryRepository(listOf(document)),
            documentWorkspace = workspace,
            dispatcher = openDispatcher
        )
    )

    private fun pdfInLibrary(pageCount: Int): DocumentInfo {
        val file = File(library, "book-${kotlin.random.Random.nextInt()}.pdf")

        PDDocument().use { document ->
            repeat(pageCount) { document.addPage(PDPage(PDRectangle.A4)) }
            document.save(file)
        }

        return DocumentInfo(
            document = PickedDocument(
                fileName = file.name,
                extension = "pdf",
                location = file.absolutePath
            ),
            type = DocumentType.PDF,
            exists = true,
            sizeBytes = file.length()
        )
    }

    private class FakeLibraryRepository(
        initialDocuments: List<DocumentInfo> = emptyList()
    ) : LibraryRepository {
        private val documents = initialDocuments.toMutableList()

        override suspend fun addDocument(document: DocumentInfo) {
            documents.add(document)
        }

        override suspend fun getDocuments(): List<DocumentInfo> = documents.toList()

        override suspend fun getDocument(location: String): DocumentInfo? =
            documents.firstOrNull { it.document.location == location }

        override suspend fun setFavorite(location: String, isFavorite: Boolean) = Unit

        override suspend fun removeDocument(location: String) {
            documents.removeAll { it.document.location == location }
        }
    }
}
