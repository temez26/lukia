package io.lukia.features.library.presentation

import io.lukia.features.import.TestDocuments
import io.lukia.features.import.domain.DocumentAnalyzer
import io.lukia.features.import.domain.DocumentInfo
import io.lukia.features.import.domain.DocumentType
import io.lukia.features.import.domain.ImportDocumentUseCase
import io.lukia.features.import.domain.PickedDocument
import io.lukia.features.library.domain.GetLibraryDocumentsUseCase
import io.lukia.features.library.domain.LibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadsDocumentsOnInit() = runTest {
        val documentInfo = pdfDocumentInfo()
        val repository = FakeLibraryRepository(initialDocuments = listOf(documentInfo))
        val viewModel = createViewModel(repository)

        assertEquals(listOf(documentInfo), viewModel.documents.value)
    }

    @Test
    fun importingDocumentReloadsLibraryAndClearsError() = runTest {
        val documentInfo = pdfDocumentInfo()
        val repository = FakeLibraryRepository()
        val analyzer = FakeDocumentAnalyzer(documentInfo)
        val viewModel = createViewModel(repository, analyzer)

        viewModel.importDocument(documentInfo.document)

        assertEquals(listOf(documentInfo), viewModel.documents.value)
        assertNull(viewModel.errorMessage.value)
    }

    @Test
    fun importFailureSetsErrorMessageAndKeepsLibraryUnchanged() = runTest {
        val analyzer = FakeDocumentAnalyzer(
            DocumentInfo(
                document = TestDocuments.txt(location = "/test/book.txt"),
                type = DocumentType.UNKNOWN,
                exists = true,
                sizeBytes = 10L
            )
        )
        val repository = FakeLibraryRepository()
        val viewModel = createViewModel(repository, analyzer)

        viewModel.importDocument(TestDocuments.txt(location = "/test/book.txt"))

        assertEquals(emptyList(), viewModel.documents.value)
        assertEquals("Unsupported file type", viewModel.errorMessage.value)
    }

    @Test
    fun clearErrorMessageResetsError() = runTest {
        val analyzer = FakeDocumentAnalyzer(
            DocumentInfo(
                document = TestDocuments.txt(location = "/test/book.txt"),
                type = DocumentType.UNKNOWN,
                exists = true,
                sizeBytes = 10L
            )
        )
        val viewModel = createViewModel(FakeLibraryRepository(), analyzer)
        viewModel.importDocument(TestDocuments.txt(location = "/test/book.txt"))

        viewModel.clearErrorMessage()

        assertNull(viewModel.errorMessage.value)
    }

    private fun createViewModel(
        repository: LibraryRepository,
        analyzer: DocumentAnalyzer = FakeDocumentAnalyzer(pdfDocumentInfo())
    ): LibraryViewModel {
        return LibraryViewModel(
            getLibraryDocumentsUseCase = GetLibraryDocumentsUseCase(repository),
            importDocumentUseCase = ImportDocumentUseCase(
                documentAnalyzer = analyzer,
                libraryRepository = repository,
                // The use case moves work off the main dispatcher in
                // production; pinning it here keeps the import synchronous so
                // state can be asserted immediately after the call.
                dispatcher = UnconfinedTestDispatcher()
            )
        )
    }

    private fun pdfDocumentInfo() = DocumentInfo(
        document = TestDocuments.pdf(location = "/test/book.pdf"),
        type = DocumentType.PDF,
        exists = true,
        sizeBytes = 1024L
    )

    private class FakeDocumentAnalyzer(
        private val documentInfo: DocumentInfo
    ) : DocumentAnalyzer {
        override suspend fun analyze(document: PickedDocument): DocumentInfo {
            return documentInfo
        }
    }

    private class FakeLibraryRepository(
        initialDocuments: List<DocumentInfo> = emptyList()
    ) : LibraryRepository {
        private val documents = initialDocuments.toMutableList()

        override suspend fun addDocument(document: DocumentInfo) {
            documents.add(document)
        }

        override suspend fun getDocuments(): List<DocumentInfo> {
            return documents.toList()
        }

        override suspend fun getDocument(location: String): DocumentInfo? {
            return documents.firstOrNull { it.document.location == location }
        }
    }
}
