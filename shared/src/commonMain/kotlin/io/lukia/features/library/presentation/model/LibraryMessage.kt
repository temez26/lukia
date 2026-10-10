package io.lukia.features.library.presentation.model

import io.lukia.core.domain.AppError

/**
 * Something the library has to tell the reader, as what happened rather than as
 * the words for it.
 *
 * A view model that builds its own sentence has decided what language the app
 * speaks, and a `StateFlow<String>` is where that decision hides: by the time
 * the screen collects it the counts are gone and only English is left. So the
 * counts travel, and the screen - which has a composition and therefore a locale
 * - turns them into a sentence. See `LibraryMessageDisplay.kt`.
 *
 * Every case is one whole sentence at the other end, never a fragment.
 */
sealed interface LibraryMessage {

    /** Anything that went wrong, said the way every other failure is said. */
    data class Failed(val error: AppError) : LibraryMessage

    /** A book read again from disk, and the details it gave up this time. */
    data object DetailsRefreshed : LibraryMessage

    /**
     * Books were picked that the library already holds, and nothing else
     * happened - so there is no count of arrivals worth giving.
     */
    data class AlreadyInLibrary(val count: Int) : LibraryMessage

    /**
     * An import that did not go entirely to plan. A clean one says nothing at
     * all - see [ImportSummary.notice] - so at least one of [alreadyInLibrary]
     * and [failed] is above zero wherever this is built.
     */
    data class Imported(
        val imported: Int,
        val alreadyInLibrary: Int,
        val failed: Int,
    ) : LibraryMessage

    /**
     * One thing done to several books at once, and how many of them it reached.
     *
     * [operation] is the enum rather than the word for it. The verb used to be
     * looked up by the English label the progress bar was showing, which made
     * translating that label a way to silently change what the report said.
     */
    data class BulkDone(
        val operation: BulkOperation,
        val done: Int,
        val failed: Int,
    ) : LibraryMessage
}
