package io.lukia.features.bookdetail.application

import io.lukia.features.library.domain.LibraryRepository
import io.lukia.features.reader.domain.ReadingProgressRepository

/**
 * Everything known about one book, gathered for the screen that shows it.
 *
 * The library entry decides whether there is anything here at all: a location
 * the library has no record of is a book that is not in it, whatever a stored
 * reading position might say about it.
 */
class GetDocumentDetailsUseCase(
    private val libraryRepository: LibraryRepository,
    private val readingProgressRepository: ReadingProgressRepository
) {
    suspend operator fun invoke(location: String): BookDetails? {
        val document = libraryRepository.getDocument(location) ?: return null

        return BookDetails(
            document = document,
            progress = readingProgressRepository.progressFor(location)
        )
    }
}
