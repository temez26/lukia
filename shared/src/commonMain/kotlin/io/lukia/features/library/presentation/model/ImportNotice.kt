package io.lukia.features.library.presentation.model

import io.lukia.features.library.application.ImportSummary

/**
 * How far through an import the library is, for the notice the screen shows
 * while it runs.
 */
data class ImportProgress(
    val completed: Int,
    val total: Int,
)

/**
 * What the snackbar says about a finished import, or null when there is nothing
 * worth saying.
 *
 * Silence on a clean import is deliberate: the books appearing in the grid are
 * the report, and confirming what the reader can see is one more thing to
 * dismiss. A notice is earned only when some of what was chosen did not arrive,
 * and leads with the count that did - "0 imported" explains an unchanged library.
 *
 * The counts travel rather than the words for them - see [LibraryMessage].
 */
fun ImportSummary.notice(): LibraryMessage? {
    if (failed == 0 && alreadyInLibrary == 0) {
        return null
    }

    // Nothing went wrong and nothing was new: the reader picked books they
    // already have, and a count would only invite them to work out that this is
    // what happened.
    if (failed == 0 && imported == 0) {
        return LibraryMessage.AlreadyInLibrary(alreadyInLibrary)
    }

    return LibraryMessage.Imported(
        imported = imported,
        alreadyInLibrary = alreadyInLibrary,
        failed = failed,
    )
}
