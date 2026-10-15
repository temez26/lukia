package io.lukia.features.library.presentation.model

import androidx.compose.runtime.Composable
import io.lukia.core.presentation.displayShortMessage
import io.lukia.resources.Res
import io.lukia.resources.library_already_in_library
import io.lukia.resources.library_bulk_deleted
import io.lukia.resources.library_bulk_refreshed
import io.lukia.resources.library_bulk_reset
import io.lukia.resources.library_bulk_running_delete
import io.lukia.resources.library_bulk_running_favourite
import io.lukia.resources.library_bulk_running_refresh
import io.lukia.resources.library_bulk_running_reset
import io.lukia.resources.library_bulk_running_unfavourite
import io.lukia.resources.library_bulk_running_upload
import io.lukia.resources.library_bulk_skipped
import io.lukia.resources.library_bulk_updated
import io.lukia.resources.library_bulk_uploaded
import io.lukia.resources.library_details_refreshed
import io.lukia.resources.library_imported_mixed
import io.lukia.resources.library_imported_with_existing
import io.lukia.resources.library_imported_with_failures
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The single place a [LibraryMessage] turns into words, following the same
 * pattern as `AppErrorDisplay.kt`.
 *
 * Composable, because that is what a string resource needs and what makes the
 * sentence follow the reader's language rather than the language the app
 * happened to be in when the action was started.
 */
@Composable
fun LibraryMessage.displayText(): String = when (this) {
    is LibraryMessage.Failed -> error.displayShortMessage()

    LibraryMessage.DetailsRefreshed -> stringResource(Res.string.library_details_refreshed)

    is LibraryMessage.AlreadyInLibrary ->
        pluralStringResource(Res.plurals.library_already_in_library, count, count)

    is LibraryMessage.Imported -> importedText()

    is LibraryMessage.BulkDone -> bulkText()
}

/**
 * What a bulk action is doing, while it is doing it.
 *
 * Its own string per operation rather than a verb dropped into a shared frame:
 * "Refreshing 3 of 40" and "Deleting 3 of 40" are two sentences in most
 * languages, and only one of them can be made by substitution.
 */
@Composable
fun BulkAction.displayText(): String =
    stringResource(operation.runningFormat(), completed, total)

/**
 * Three whole sentences rather than a list joined with commas.
 *
 * The list was the bug: a translator was handed "Imported %d", "%d already
 * added" and "%d skipped" as three unrelated strings and no way to say which
 * order they go in, or that the last two are subordinate to the first.
 */
@Composable
private fun LibraryMessage.Imported.importedText(): String = when {
    alreadyInLibrary > 0 && failed > 0 -> stringResource(
        Res.string.library_imported_mixed,
        imported,
        alreadyInLibrary,
        failed,
    )

    alreadyInLibrary > 0 ->
        stringResource(Res.string.library_imported_with_existing, imported, alreadyInLibrary)

    else -> stringResource(Res.string.library_imported_with_failures, imported, failed)
}

/**
 * The report, and the count of books it could not reach wrapped around it.
 *
 * The skipped count wraps rather than being appended, so a language that puts
 * the caveat first has somewhere to put it.
 */
@Composable
private fun LibraryMessage.BulkDone.bulkText(): String {
    val report = pluralStringResource(operation.doneQuantity(), done, done)

    if (failed == 0) return report

    return pluralStringResource(Res.plurals.library_bulk_skipped, failed, report, failed)
}

/**
 * Favouriting and unfavouriting report as one thing done, because that is what
 * the reader asked for - the two directions of one control - and because "8
 * books favourited" reads as a claim about the books rather than about the act.
 */
private fun BulkOperation.doneQuantity() = when (this) {
    BulkOperation.Refresh -> Res.plurals.library_bulk_refreshed
    BulkOperation.Favourite, BulkOperation.Unfavourite -> Res.plurals.library_bulk_updated
    BulkOperation.Reset -> Res.plurals.library_bulk_reset
    BulkOperation.Delete -> Res.plurals.library_bulk_deleted
    BulkOperation.Upload -> Res.plurals.library_bulk_uploaded
}

private fun BulkOperation.runningFormat(): StringResource = when (this) {
    BulkOperation.Refresh -> Res.string.library_bulk_running_refresh
    BulkOperation.Favourite -> Res.string.library_bulk_running_favourite
    BulkOperation.Unfavourite -> Res.string.library_bulk_running_unfavourite
    BulkOperation.Reset -> Res.string.library_bulk_running_reset
    BulkOperation.Delete -> Res.string.library_bulk_running_delete
    BulkOperation.Upload -> Res.string.library_bulk_running_upload
}
