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
 * Silence on a clean import is deliberate: the books appearing in the grid is
 * the report, and a message confirming what the reader can already see is one
 * more thing to dismiss. A notice is only earned when some of what was chosen
 * did not arrive, and it always leads with the count that did - "0 imported" is
 * the sentence that explains an unchanged library.
 */
fun ImportSummary.notice(): String? {
    if (failed == 0 && alreadyInLibrary == 0) {
        return null
    }

    // Nothing went wrong and nothing was new: the reader picked books they
    // already have, and a count would only invite them to work out that this is
    // what happened.
    if (failed == 0 && imported == 0) {
        return if (alreadyInLibrary == 1) {
            "Already in your library"
        } else {
            "All $alreadyInLibrary already in your library"
        }
    }

    return buildList {
        add("Imported $imported")
        if (alreadyInLibrary > 0) add("$alreadyInLibrary already added")
        if (failed > 0) add("$failed skipped")
    }.joinToString(", ")
}
