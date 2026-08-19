package io.lukia.features.library.application

import io.lukia.core.domain.AppError
import io.lukia.core.domain.DocumentInfo
import io.lukia.core.domain.asFailure
import io.lukia.features.library.domain.LibraryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads a book the library already holds again, so that a cover or a title the
 * import could not make sense of - or an edition of the file that has been
 * replaced on disk since - is picked up without removing and re-adding it.
 *
 * There is otherwise no way to ask for this: a folder rescan leaves alone
 * everything already on the shelf, which is what makes rescanning a large
 * library cheap, and that has to be a deliberate action rather than a side
 * effect of importing.
 */
class RefreshDocumentUseCase(
    private val libraryRepository: LibraryRepository,
    private val importDocumentUseCase: ImportDocumentUseCase,
    /** Injectable so tests can refresh deterministically. */
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    suspend operator fun invoke(location: String): Result<DocumentInfo> =
        withContext(dispatcher) {
            val stored = libraryRepository.getDocument(location)
                ?: return@withContext AppError.DocumentNotFound.asFailure()

            // Re-analysing a book is exactly importing the file again, so the
            // rules about what may be stored stay in one place. The store keeps
            // the favourite and the shelf position through it, both of which
            // belong to the reader rather than to the file.
            importDocumentUseCase(stored.document).map {
                // Read back rather than handed on from the analysis: the store
                // keeps the previous cover and metadata where this reading found
                // none, so only the stored row is the whole truth about the book.
                libraryRepository.getDocument(location) ?: it
            }
        }
}
