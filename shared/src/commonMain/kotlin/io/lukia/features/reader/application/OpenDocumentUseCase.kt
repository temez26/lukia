package io.lukia.features.reader.application

import io.lukia.core.domain.AppError
import io.lukia.core.domain.DocumentType
import io.lukia.core.domain.DocumentWorkspace
import io.lukia.core.domain.asFailure
import io.lukia.features.library.domain.LibraryRepository
import io.lukia.features.reader.domain.ReaderDocument
import io.lukia.features.reader.infrastructure.openPdfPages
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Opens a book for reading: finds it in the library, makes sure there is a
 * working copy of it in app storage, and opens that copy's pages.
 *
 * The caller owns the returned [ReaderDocument] and has to close it.
 */
class OpenDocumentUseCase(
    private val libraryRepository: LibraryRepository,
    private val documentWorkspace: DocumentWorkspace,
    /** Injectable so tests can open documents deterministically. */
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    /**
     * Copying a book and parsing its page tree both run for as long as the file
     * is large. Callers open from `viewModelScope`, which is main-dispatched, so
     * without this switch a big PDF freezes the window on the way in.
     */
    suspend operator fun invoke(location: String): Result<ReaderDocument> =
        withContext(dispatcher) {
            val documentInfo = libraryRepository.getDocument(location)
                ?: return@withContext AppError.DocumentNotFound.asFailure()

            if (documentInfo.type != DocumentType.PDF) {
                return@withContext AppError.FormatNotReadable.asFailure()
            }

            val workingCopy = documentWorkspace.workingCopy(documentInfo)
            val pages = openPdfPages(workingCopy.path)

            // A document that opened but has nothing in it would otherwise show
            // as an empty reader, which reads as the app having lost the book.
            if (pages.pageCount == 0) {
                pages.close()
                return@withContext AppError.Unexpected("This document has no pages.").asFailure()
            }

            Result.success(
                ReaderDocument(
                    info = documentInfo,
                    workingCopy = workingCopy,
                    pages = pages
                )
            )
        }
}
