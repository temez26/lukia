package io.lukia.features.sync.application

import io.lukia.core.domain.document.ServerBookUploader
import io.lukia.features.account.domain.AccountFailure
import io.lukia.features.account.domain.AccountProblem
import io.lukia.features.library.domain.ServerShelf
import io.lukia.features.sync.infrastructure.SyncCursors
import io.lukia.features.sync.infrastructure.UploadChoice
import kotlin.coroutines.cancellation.CancellationException

/**
 * Adds the books imported since signing in to the server, so a book added on
 * one device is on every device without anybody sending it. Only new books: a
 * library the device had before signing in is the reader's to share or not,
 * one book at a time.
 *
 * A server that cannot be reached stops the pass and is tried again next
 * round; a book it refuses - too large, or not a book after all - is left on
 * this device and not offered again.
 */
class UploadNewBooksUseCase(
    private val shelf: ServerShelf,
    private val uploader: ServerBookUploader,
    private val cursors: SyncCursors,
    private val choice: UploadChoice,
) {
    suspend operator fun invoke() {
        val after = cursors.uploadsAfter
        if (after == null) {
            // The first round since signing in: what is on the shelf now is the
            // library the device came with, and everything after it is new.
            cursors.uploadsAfter = shelf.newestPosition()
            return
        }
        if (!choice.enabled) return

        val refused = cursors.refusedUploads
        shelf.localOnlyAfter(after).filterNot { it in refused }.forEach { location ->
            try {
                uploader.upload(location) {}
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: AccountFailure) {
                if (failure.problem in TryAgainLater) throw failure
                cursors.refusedUploads = cursors.refusedUploads + location
            }
        }
    }

    private companion object {
        /** Problems with the connection or the sign-in rather than with the book. */
        val TryAgainLater = setOf(AccountProblem.ServerUnreachable, AccountProblem.SessionExpired)
    }
}
