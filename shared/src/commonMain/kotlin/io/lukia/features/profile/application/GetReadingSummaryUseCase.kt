package io.lukia.features.profile.application

import io.lukia.features.library.domain.LibraryRepository
import io.lukia.features.profile.domain.ReadingSummary
import io.lukia.features.profile.domain.readingSummary
import io.lukia.features.reader.domain.ReadingProgressRepository

/**
 * Reads the library and the positions stored against it, and reduces the two to
 * what a profile has to say - see [readingSummary].
 *
 * It reads the two stores directly rather than borrowing the library screen's
 * ViewModel: the profile is reached from a menu rather than from the shelf, and
 * on desktop it opens over a screen whose ViewModel is scoped to a different
 * back stack entry. Both reads are cheap next to what the grid already does with
 * the same rows.
 */
class GetReadingSummaryUseCase(
    private val library: LibraryRepository,
    private val progress: ReadingProgressRepository,
) {
    suspend operator fun invoke(): ReadingSummary =
        readingSummary(library.getDocuments(), progress.allProgress())
}
