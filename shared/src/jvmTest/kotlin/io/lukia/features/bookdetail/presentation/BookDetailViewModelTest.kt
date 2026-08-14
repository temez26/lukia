package io.lukia.features.bookdetail.presentation

import io.lukia.features.bookdetail.application.GetDocumentDetailsUseCase
import io.lukia.core.domain.TestDocuments
import io.lukia.core.domain.DocumentInfo
import io.lukia.core.domain.DocumentType
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
import kotlin.test.assertFalse
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class BookDetailViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadsMatchingDocumentOnInit() = runTest {
        val documentInfo = DocumentInfo(
            document = TestDocuments.pdf(location = "/test/book.pdf"),
            type = DocumentType.PDF,
            exists = true,
            sizeBytes = 1024L
        )
        val repository = FakeLibraryRepository(initialDocuments = listOf(documentInfo))
        val viewModel = createViewModel(location = "/test/book.pdf", repository = repository)

        assertEquals(documentInfo, viewModel.document.value)
        assertFalse(viewModel.isLoading.value)
    }

    @Test
    fun exposesNullDocumentWhenLocationHasNoMatch() = runTest {
        val repository = FakeLibraryRepository()
        val viewModel = createViewModel(location = "/test/missing.pdf", repository = repository)

        assertNull(viewModel.document.value)
        assertFalse(viewModel.isLoading.value)
    }

    private fun createViewModel(
        location: String,
        repository: LibraryRepository
    ): BookDetailViewModel {
        return BookDetailViewModel(
            location = location,
            getDocumentDetailsUseCase = GetDocumentDetailsUseCase(repository)
        )
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

        override suspend fun setFavorite(location: String, isFavorite: Boolean) {
            val index = documents.indexOfFirst { it.document.location == location }
            if (index >= 0) {
                documents[index] = documents[index].copy(isFavorite = isFavorite)
            }
        }

        override suspend fun removeDocument(location: String) {
            documents.removeAll { it.document.location == location }
        }
    }
}
