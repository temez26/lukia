package io.lukia.features.library.application

import io.lukia.core.domain.AppError
import io.lukia.core.domain.asFailure
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.features.library.domain.DocumentAnalyzer
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
 *
 * It is also the only thing that can find out a book's file has gone, and the
 * only thing that writes that down - see the note on `fileExists` in
 * `Document.sq`, which calls a rescan the thing that corrects it. Nothing else
 * looks: asking after every book on a shelf of five thousand is five thousand
 * trips to a file system or a content provider, which is not something to do
 * because a library was opened.
 */
class RefreshDocumentUseCase(
    private val libraryRepository: LibraryRepository,
    private val importDocumentUseCase: ImportDocumentUseCase,
    /** Asked whether the file is still there, which is the cheap half of an import. */
    private val documentAnalyzer: DocumentAnalyzer,
    /** Injectable so tests can refresh deterministically. */
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    suspend operator fun invoke(location: String): Result<DocumentInfo> =
        withContext(dispatcher) {
            val stored = libraryRepository.getDocument(location)
                ?: return@withContext AppError.DocumentNotFound.asFailure()

            // Recorded rather than only reported. The reader is told either way,
            // but a message is gone by the next screen, and what makes the shelf
            // able to offer "three of your books have moved" is the row
            // remembering. Everything else about the book is left exactly as it
            // was - a file that comes back should find its cover, its metadata
            // and its place on the shelf still there.
            if (!documentAnalyzer.exists(stored.document)) {
                libraryRepository.addDocument(stored.copy(exists = false))

                return@withContext AppError.DocumentNotFound.asFailure()
            }

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
