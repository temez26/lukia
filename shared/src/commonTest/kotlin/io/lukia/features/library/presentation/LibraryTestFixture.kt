package io.lukia.features.library.presentation

import io.lukia.core.domain.document.CoverImage
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.domain.document.DocumentType
import io.lukia.core.domain.document.FakeIncomingDocumentStore
import io.lukia.core.domain.document.PickedDocument
import io.lukia.core.domain.document.PickedFolder
import io.lukia.core.domain.document.TestDocuments
import io.lukia.core.domain.reading.FakePdfLayoutRepository
import io.lukia.core.domain.reading.FakeReadingProgressRepository
import io.lukia.core.domain.reading.ReadingProgress
import io.lukia.core.domain.reading.ReadingProgressRepository
import io.lukia.core.domain.storage.FakeDocumentWorkspace
import io.lukia.core.domain.storage.FakeSettingsStore
import io.lukia.core.infrastructure.DocumentInbox
import io.lukia.core.infrastructure.IncomingDocuments
import io.lukia.core.presentation.UiState
import io.lukia.features.library.application.GetLibraryDocumentsUseCase
import io.lukia.features.library.application.ImportDocumentUseCase
import io.lukia.features.library.application.ImportDocumentsUseCase
import io.lukia.features.library.application.ImportFolderUseCase
import io.lukia.features.library.application.RefreshDocumentUseCase
import io.lukia.features.library.application.RemoveDocumentUseCase
import io.lukia.features.library.application.SetFavoriteUseCase
import io.lukia.features.library.domain.DocumentAnalyzer
import io.lukia.features.library.domain.FolderScanner
import io.lukia.features.library.domain.LibraryRepository
import io.lukia.features.library.infrastructure.LibrarySettingsRepository
import io.lukia.features.library.infrastructure.RecentSearchesRepository
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * A library ViewModel wired to fakes, and the books it is given.
 *
 * Shared by the library test classes rather than repeated in each, so that a
 * change to what the ViewModel needs is made once.
 */

internal class ThrowingProgressRepository : ReadingProgressRepository {
    override suspend fun progressFor(location: String): ReadingProgress? = null

    override suspend fun allProgress(): Map<String, ReadingProgress> = error("position store blew up")

    override suspend fun save(location: String, progress: ReadingProgress) = Unit

    override suspend fun forget(location: String) = Unit
}

/** Fails the assertion rather than returning null when the state is not Success. */
internal fun LibraryViewModel.documents(): List<DocumentInfo> {
    val state = uiState.value
    return (state as? UiState.Success)?.data
        ?: throw AssertionError("expected a Success state, was $state")
}

internal fun createViewModel(
    repository: LibraryRepository,
    analyzer: DocumentAnalyzer = FakeDocumentAnalyzer(pdfDocumentInfo()),
    folderScanner: FolderScanner = FakeFolderScanner(emptyList()),
    readingProgress: ReadingProgressRepository = FakeReadingProgressRepository(),
    /** Passed in where a test wants to deliver a book to the app's inbox. */
    incomingDocuments: DocumentInbox = DocumentInbox(),
    incomingDocumentStore: FakeIncomingDocumentStore = FakeIncomingDocumentStore(),
): LibraryViewModel {
    // The use cases move work off the main dispatcher in production;
    // pinning it here keeps imports synchronous so state can be asserted
    // immediately after the call.
    val importDocumentsUseCase = ImportDocumentsUseCase(
        importDocumentUseCase = ImportDocumentUseCase(
            documentAnalyzer = analyzer,
            libraryRepository = repository,
            dispatcher = UnconfinedTestDispatcher(),
        ),
        libraryRepository = repository,
        dispatcher = UnconfinedTestDispatcher(),
    )

    return LibraryViewModel(
        getLibraryDocumentsUseCase = GetLibraryDocumentsUseCase(repository),
        importDocumentsUseCase = importDocumentsUseCase,
        importFolderUseCase = ImportFolderUseCase(
            folderScanner = folderScanner,
            importDocumentsUseCase = importDocumentsUseCase,
            dispatcher = UnconfinedTestDispatcher(),
        ),
        incoming = IncomingDocuments(inbox = incomingDocuments, store = incomingDocumentStore),
        setFavoriteUseCase = SetFavoriteUseCase(repository),
        refreshDocumentUseCase = RefreshDocumentUseCase(
            libraryRepository = repository,
            importDocumentUseCase = ImportDocumentUseCase(
                documentAnalyzer = analyzer,
                libraryRepository = repository,
                dispatcher = UnconfinedTestDispatcher(),
            ),
            dispatcher = UnconfinedTestDispatcher(),
        ),
        removeDocumentUseCase = RemoveDocumentUseCase(
            repository,
            FakeDocumentWorkspace(),
            FakeReadingProgressRepository(),
            FakePdfLayoutRepository(),
        ),
        readingProgressRepository = readingProgress,
        librarySettingsRepository = LibrarySettingsRepository(FakeSettingsStore()),
        recentSearchesRepository = RecentSearchesRepository(FakeSettingsStore()),
        // The narrowing runs off the main thread in production; pinning it
        // here means what the grid would show can be read straight after the
        // call that changed it.
        dispatcher = UnconfinedTestDispatcher(),
    )
}

internal val booksFolder = PickedFolder(
    name = "Books",
    location = "/test",
)

internal fun pdfDocumentInfo(
    location: String = "/test/book.pdf",
    favorite: Boolean = false,
    cover: ByteArray? = null,
) = DocumentInfo(
    document = TestDocuments.pdf(location = location),
    type = DocumentType.PDF,
    exists = true,
    sizeBytes = 1024L,
    isFavorite = favorite,
    coverImage = cover?.let(::CoverImage),
)

internal class FakeDocumentAnalyzer(
    internal val documentInfo: DocumentInfo,
) : DocumentAnalyzer {
    override suspend fun analyze(document: PickedDocument): DocumentInfo = documentInfo
}

internal class ThrowingDocumentAnalyzer : DocumentAnalyzer {
    override suspend fun analyze(
        document: PickedDocument,
    ): DocumentInfo = error("analysis blew up")
}

internal class FakeFolderScanner(
    internal val documents: List<PickedDocument>,
) : FolderScanner {
    override suspend fun scan(folder: PickedFolder): List<PickedDocument> = documents
}

internal class FakeLibraryRepository(
    initialDocuments: List<DocumentInfo> = emptyList(),
) : LibraryRepository {
    internal val documents = initialDocuments.toMutableList()

    /**
     * Replaces by location, and keeps the favourite through it, as the real
     * store does - the flag belongs to the reader, not to the file.
     */
    override suspend fun addDocument(document: DocumentInfo) {
        val index = documents.indexOfFirst {
            it.document.location == document.document.location
        }

        if (index >= 0) {
            documents[index] = document.copy(isFavorite = documents[index].isFavorite)
        } else {
            documents.add(document)
        }
    }

    /** The shelf without its artwork, as the real store answers it. */
    override suspend fun getDocuments(): List<DocumentInfo> =
        documents.map { it.copy(coverImage = null) }

    override suspend fun getDocument(location: String): DocumentInfo? = documents.firstOrNull {
        it.document.location == location
    }

    /** Read by location, exactly as the real store reads it. */
    override suspend fun coverFor(location: String): CoverImage? =
        getDocument(location)?.coverImage

    /** Answered the expensive way; nothing in a test cares what it costs. */
    override suspend fun knownLocations(): Set<String> =
        getDocuments().mapTo(mutableSetOf()) { it.document.location }

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

internal class ThrowingLibraryRepository : LibraryRepository {
    override suspend fun addDocument(document: DocumentInfo) = Unit

    override suspend fun getDocuments(): List<DocumentInfo> = error("library read blew up")

    override suspend fun getDocument(location: String): DocumentInfo? = null

    /** Read by location, exactly as the real store reads it. */
    override suspend fun coverFor(location: String): CoverImage? =
        getDocument(location)?.coverImage

    /** Answered the expensive way; nothing in a test cares what it costs. */
    override suspend fun knownLocations(): Set<String> =
        getDocuments().mapTo(mutableSetOf()) { it.document.location }

    override suspend fun setFavorite(location: String, isFavorite: Boolean) = Unit

    override suspend fun removeDocument(location: String) = Unit
}
