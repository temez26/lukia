package io.lukia.features.library.application

import io.lukia.core.domain.document.CoverImage
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.features.library.domain.LibraryRepository

/**
 * The library, as the shelf shows it.
 *
 * In two halves because the read is in two halves. The rows come back without
 * their artwork - a library's worth of covers is hundreds of megabytes held to
 * draw the dozen the reader can see - and whatever draws one asks for it by name
 * as it does. See [io.lukia.core.domain.document.DocumentCatalogue.getDocuments].
 */
class GetLibraryDocumentsUseCase(
    private val repository: LibraryRepository,
) {
    suspend operator fun invoke(): List<DocumentInfo> = repository.getDocuments()

    /** One book's artwork, for the card that is about to draw it. */
    suspend fun coverFor(location: String): CoverImage? = repository.coverFor(location)
}
