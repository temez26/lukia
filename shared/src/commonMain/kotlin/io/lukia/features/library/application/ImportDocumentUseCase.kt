package io.lukia.features.library.application

import io.lukia.core.domain.AppError
import io.lukia.core.domain.DocumentInfo
import io.lukia.core.domain.DocumentType
import io.lukia.core.domain.asFailure
import io.lukia.core.domain.PickedDocument
import io.lukia.features.import.domain.DocumentAnalyzer
import io.lukia.features.library.domain.LibraryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ImportDocumentUseCase(
    private val documentAnalyzer: DocumentAnalyzer,
    private val libraryRepository: LibraryRepository,
    /** Injectable so tests can run the import deterministically. */
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
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
        withContext(dispatcher) {
            val documentInfo = documentAnalyzer.analyze(pickedDocument)

            if (documentInfo.type == DocumentType.UNKNOWN) {
                return@withContext AppError.UnsupportedFileType.asFailure()
            }

            libraryRepository.addDocument(documentInfo)
            Result.success(documentInfo)
        }
}