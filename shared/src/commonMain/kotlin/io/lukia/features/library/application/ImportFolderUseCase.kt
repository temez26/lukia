package io.lukia.features.library.application

import io.lukia.core.domain.PickedFolder
import io.lukia.features.import.domain.FolderScanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class FolderImportSummary(
    val imported: Int,
    val failed: Int
)

/**
 * Imports every supported document in a folder by delegating each file to
 * [ImportDocumentUseCase], so analysis and cover extraction stay in one place.
 */
class ImportFolderUseCase(
    private val folderScanner: FolderScanner,
    private val importDocumentUseCase: ImportDocumentUseCase,
    /** Injectable so tests can run the import deterministically. */
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    suspend operator fun invoke(folder: PickedFolder): Result<FolderImportSummary> =
        withContext(dispatcher) {
            val documents = folderScanner.scan(folder)

            if (documents.isEmpty()) {
                return@withContext Result.failure(
                    Exception("No PDF or EPUB files found in this folder")
                )
            }

            var imported = 0
            var failed = 0

            // Sequential on purpose: ImportDocumentUseCase does CPU-bound cover
            // rendering, so fanning out would just contend for the same cores
            // and spike memory on large folders.
            documents.forEach { document ->
                val result = try {
                    importDocumentUseCase(document)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    // ImportDocumentUseCase lets analyzer errors propagate
                    // instead of returning a failure, so one unreadable file
                    // would otherwise abort the rest of the folder.
                    Result.failure(error)
                }

                result
                    .onSuccess { imported++ }
                    .onFailure { failed++ }
            }

            Result.success(FolderImportSummary(imported, failed))
        }
}
