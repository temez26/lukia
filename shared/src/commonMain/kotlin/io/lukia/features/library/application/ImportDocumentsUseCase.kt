package io.lukia.features.library.application

import io.lukia.core.domain.document.PickedDocument
import io.lukia.features.library.domain.LibraryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Imports a batch of picked files by delegating each one to
 * [ImportDocumentUseCase], so analysis and cover extraction stay in one place.
 *
 * Every route into the library that can bring in more than one file comes
 * through here - a multi-selection from the file picker, and a whole folder by
 * way of [ImportFolderUseCase] - so counting, skipping and progress are decided
 * once rather than once per caller.
 */
class ImportDocumentsUseCase(
    private val importDocumentUseCase: ImportDocumentUseCase,
    private val libraryRepository: LibraryRepository,
    /** Injectable so tests can run the import deterministically. */
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    /**
     * [onProgress] is called after each file with how many of [documents] are
     * done. It runs on [dispatcher] rather than the caller's thread, so what it
     * touches has to be safe to touch from there.
     *
     * A batch of several never fails as a whole - one unreadable file is counted
     * and the rest still arrive. Only a batch of one can come back a failure,
     * carrying the reason that single file did not import.
     */
    // The batch is the boundary: whatever one file's import throws is counted as
    // that file failing and the rest still arrive, so narrowing this would let an
    // unnamed failure take the other four hundred books with it.
    @Suppress("TooGenericExceptionCaught")
    suspend operator fun invoke(
        documents: List<PickedDocument>,
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> },
    ): Result<ImportSummary> = withContext(dispatcher) {
        // Read once, up front. Analysing a file means opening it and rendering a
        // cover, so a folder that is rescanned after two books were added to it
        // should cost two imports rather than five hundred.
        val known = libraryRepository.knownLocations()

        // One file named twice is one file. A folder walked through a symlink
        // back into itself, or a picker that answers with a selection twice,
        // hands the same location in more than once - and every one of those
        // repeats was a second reading of the file and a second cover rendered,
        // to produce the row the first one already stored. The count said two
        // books arrived where one did.
        val picked = documents.distinctBy { it.location }

        picked.singleOrNull()?.let { only ->
            onProgress(1, 1)

            if (only.location in known) {
                return@withContext Result.success(
                    ImportSummary(imported = 0, alreadyInLibrary = 1, failed = 0),
                )
            }

            // One file chosen is one outcome, so its failure is the whole
            // answer: the reader is owed the reason - that the format is not one
            // this app reads - rather than a tally of one that explains nothing.
            return@withContext importDocumentUseCase(only).map {
                ImportSummary(imported = 1, alreadyInLibrary = 0, failed = 0)
            }
        }

        var imported = 0
        var alreadyInLibrary = 0
        var failed = 0

        // Sequential on purpose: ImportDocumentUseCase does CPU-bound cover
        // rendering, so fanning out would just contend for the same cores and
        // spike memory on large folders.
        picked.forEachIndexed { index, document ->
            if (document.location in known) {
                alreadyInLibrary++
            } else {
                val result = try {
                    importDocumentUseCase(document)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    // ImportDocumentUseCase lets analyzer errors propagate
                    // instead of returning a failure, so one unreadable file
                    // would otherwise abort the rest of the batch.
                    Result.failure(error)
                }

                result
                    .onSuccess { imported++ }
                    .onFailure { failed++ }
            }

            onProgress(index + 1, picked.size)
        }

        Result.success(
            ImportSummary(
                imported = imported,
                alreadyInLibrary = alreadyInLibrary,
                failed = failed,
            ),
        )
    }
}
