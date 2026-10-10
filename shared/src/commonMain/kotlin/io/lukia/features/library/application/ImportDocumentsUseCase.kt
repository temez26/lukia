package io.lukia.features.library.application

import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.domain.document.PickedDocument
import io.lukia.features.library.domain.LibraryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
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

        Result.success(importAll(picked, known, onProgress))
    }

    /**
     * Every picked file the library has not already got, analysed one at a time
     * and stored in groups.
     *
     * Sequential on purpose: analysing does CPU-bound cover rendering, so
     * fanning out would just contend for the same cores and spike memory on
     * large folders.
     *
     * Stored in groups because writing is the other half. A book's row is a
     * moment to write and a commit is not, so a folder of five thousand books
     * used to cost five thousand commits - each made durable before the next
     * could begin. See [DocumentsPerTransaction] for why the group is small
     * rather than the whole folder.
     */
    private suspend fun importAll(
        picked: List<PickedDocument>,
        known: Set<String>,
        onProgress: (completed: Int, total: Int) -> Unit,
    ): ImportSummary {
        var imported = 0
        var alreadyInLibrary = 0
        var failed = 0

        val pending = mutableListOf<DocumentInfo>()

        /**
         * A book counts as imported once it is actually on the shelf, so the
         * tally is moved here rather than kept where the file was read. A group
         * that cannot be written is a group of failures, which is what storing
         * them one at a time would have said too.
         */
        suspend fun store() {
            val batch = pending.size

            if (batch == 0) return

            val stored = runCatching { libraryRepository.addDocuments(pending.toList()) }
            pending.clear()

            if (stored.isSuccess) imported += batch else failed += batch
        }

        try {
            picked.forEachIndexed { index, document ->
                if (document.location in known) {
                    alreadyInLibrary++
                } else {
                    analysed(document).onSuccess { pending += it }.onFailure { failed++ }

                    if (pending.size >= DocumentsPerTransaction) store()
                }

                onProgress(index + 1, picked.size)
            }
        } finally {
            // Whatever was read but not yet written goes to the shelf, however
            // this ended. Uncancellable because stopping an import is the main
            // way here, and the promise the stop makes is that the books which
            // already arrived stay - see `LibraryImportSession.cancel`.
            withContext(NonCancellable) { store() }
        }

        return ImportSummary(
            imported = imported,
            alreadyInLibrary = alreadyInLibrary,
            failed = failed,
        )
    }

    /**
     * What one file turns out to be, with its failure counted rather than
     * thrown.
     *
     * [ImportDocumentUseCase] lets analyzer errors propagate instead of
     * returning a failure, so the batch is the boundary: whatever one file's
     * analysis throws is that file failing, and the other four hundred still
     * arrive.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun analysed(document: PickedDocument): Result<DocumentInfo> =
        try {
            importDocumentUseCase.analyse(document)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Result.failure(error)
        }
}

/**
 * How many books are written to the library at once.
 *
 * Small rather than the whole folder, because a group waits in memory with every
 * cover in it - a couple of hundred kilobytes apiece. A group of thousands would
 * be the library's artwork held at once, which is the one thing the shelf query
 * goes out of its way never to do.
 *
 * Twenty-five is a commit for every twenty-five books instead of one apiece,
 * which is where nearly all of the saving is, against a few megabytes held while
 * it happens. Going higher buys very little: the cost being avoided is the
 * commit, and it is already amortised twenty-five ways.
 *
 * Stopping an import loses none of it - what has been read is written on the way
 * out, see [importAll]. What a group does bound is a process killed mid-import,
 * which costs at most this many books their analysis; running the import again
 * picks those up rather than duplicating anything.
 */
private const val DocumentsPerTransaction = 25
