package io.lukia.features.reader.application

import io.lukia.core.domain.AppError
import io.lukia.core.domain.DocumentWorkspace
import io.lukia.core.domain.asFailure
import io.lukia.features.library.domain.LibraryRepository
import io.lukia.features.reader.domain.ReaderDocument
import io.lukia.features.reader.infrastructure.openReaderContent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Opens a book for reading: finds it in the library, makes sure there is a
 * working copy of it in app storage, and opens that copy.
 *
 * The caller owns the returned [ReaderDocument] and has to close it.
 */
class OpenDocumentUseCase(
    private val libraryRepository: LibraryRepository,
    private val documentWorkspace: DocumentWorkspace,
    /** Injectable so tests can open documents deterministically. */
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    /**
     * Copying a book and reading its structure both run for as long as the file
     * is large. Callers open from `viewModelScope`, which is main-dispatched, so
     * without this switch a big book freezes the window on the way in.
     */
    suspend operator fun invoke(location: String): Result<ReaderDocument> =
        withContext(dispatcher) {
            val documentInfo = libraryRepository.getDocument(location)
                ?: return@withContext AppError.DocumentNotFound.asFailure()

            val workingCopy = documentWorkspace.workingCopy(documentInfo)

            // Null covers both a format this app does not read at all and a file
            // whose format it does read but which turned out not to be one - a
            // `.docx` that is not a Word document is not a different failure to
            // the reader than a format it never supported.
            val content = openReaderContent(documentInfo.type, workingCopy.path)
                ?: return@withContext AppError.FormatNotReadable.asFailure()

            // A document that opened but has nothing in it would otherwise show
            // as an empty reader, which reads as the app having lost the book.
            if (content.partCount == 0) {
                content.close()

                return@withContext AppError
                    .Unexpected("This document has nothing to read.")
                    .asFailure()
            }

            Result.success(
                ReaderDocument(
                    info = documentInfo,
                    workingCopy = workingCopy,
                    content = content,
                ),
            )
        }
}
