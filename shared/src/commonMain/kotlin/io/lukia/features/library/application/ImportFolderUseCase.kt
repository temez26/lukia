package io.lukia.features.library.application

import io.lukia.core.domain.AppError
import io.lukia.core.domain.PickedFolder
import io.lukia.core.domain.asFailure
import io.lukia.features.import.domain.FolderScanner
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Imports a folder and everything filed under it: the scanner decides which
 * files count, [ImportDocumentsUseCase] brings them in.
 */
class ImportFolderUseCase(
    private val folderScanner: FolderScanner,
    private val importDocumentsUseCase: ImportDocumentsUseCase,
    /** Injectable so tests can run the import deterministically. */
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    suspend operator fun invoke(
        folder: PickedFolder,
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> },
    ): Result<ImportSummary> = withContext(dispatcher) {
        val documents = folderScanner.scan(folder)

        if (documents.isEmpty()) {
            return@withContext AppError.EmptyFolder.asFailure()
        }

        importDocumentsUseCase(documents, onProgress)
    }
}
