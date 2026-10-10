package io.lukia.features.library.application

import io.lukia.core.domain.AppError
import io.lukia.core.domain.asFailure
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.domain.document.DocumentType
import io.lukia.core.domain.document.PickedDocument
import io.lukia.features.library.domain.DocumentAnalyzer
import io.lukia.features.library.domain.LibraryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ImportDocumentUseCase(
    private val documentAnalyzer: DocumentAnalyzer,
    private val libraryRepository: LibraryRepository,
    /** Injectable so tests can run the import deterministically. */
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    /**
     * Analysis reads the whole file and renders or inflates a cover image -
     * hundreds of milliseconds for a large PDF. Callers run this from
     * `viewModelScope`, which is main-dispatched, so without this switch the
     * work lands on the UI thread and the window visibly freezes mid-import.
     *
     * [Dispatchers.Default] rather than IO: the dominant cost is decoding and
     * rendering, which is CPU-bound.
     */
    suspend operator fun invoke(pickedDocument: PickedDocument): Result<DocumentInfo> =
        analyse(pickedDocument).onSuccess { libraryRepository.addDocument(it) }

    /**
     * What the file turns out to be, without storing it.
     *
     * The expensive half on its own, for a caller bringing in a whole folder:
     * reading a book and rendering its cover is what an import spends its time
     * on, while writing the row is a moment - so a batch analyses one book at a
     * time and stores them in groups, rather than paying for a separate commit
     * per book. See [ImportDocumentsUseCase].
     *
     * A caller that takes this route owes the storing. Nothing here has written
     * anything down.
     */
    suspend fun analyse(pickedDocument: PickedDocument): Result<DocumentInfo> =
        withContext(dispatcher) {
            val documentInfo = documentAnalyzer.analyze(pickedDocument)

            if (documentInfo.type == DocumentType.UNKNOWN) {
                return@withContext AppError.UnsupportedFileType.asFailure()
            }

            Result.success(documentInfo)
        }
}
