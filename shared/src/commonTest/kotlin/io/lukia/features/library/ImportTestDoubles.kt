package io.lukia.features.library

import io.lukia.core.domain.CoverImage
import io.lukia.core.domain.DocumentInfo
import io.lukia.core.domain.DocumentType
import io.lukia.core.domain.PickedDocument
import io.lukia.features.library.domain.DocumentAnalyzer
import io.lukia.features.library.domain.LibraryRepository

/**
 * Stands in for a real analyzer: classifies by extension, and throws for
 * locations a platform analyzer would fail to open.
 */
class TypeFromExtensionAnalyzer(
    private val failingLocations: Set<String> = emptySet(),
) : DocumentAnalyzer {

    override suspend fun analyze(document: PickedDocument): DocumentInfo {
        if (document.location in failingLocations) {
            throw IllegalArgumentException(
                "File does not exist: ${document.location}",
            )
        }

        return DocumentInfo(
            document = document,
            type = DocumentType.fromExtension(document.extension),
            exists = true,
            sizeBytes = 1024L,
        )
    }
}

/** An in-memory library, keyed by location the way the real one is. */
class FakeImportLibraryRepository(
    initialDocuments: List<DocumentInfo> = emptyList(),
) : LibraryRepository {

    val documents = initialDocuments.toMutableList()

    /**
     * Keeps the favourite through a replacement, as the real store does: the
     * flag belongs to the reader and the analyser has no idea it was ever set.
     */
    override suspend fun addDocument(document: DocumentInfo) {
        val index = documents.indexOfFirst {
            it.document.location == document.document.location
        }

        if (index >= 0) {
            documents[index] = document.copy(isFavorite = documents[index].isFavorite)
        } else {
            documents.add(document)
        }
    }

    /** The shelf without its artwork, as the real store answers it. */
    override suspend fun getDocuments(): List<DocumentInfo> =
        documents.map { it.copy(coverImage = null) }

    override suspend fun getDocument(location: String): DocumentInfo? =
        documents.firstOrNull { it.document.location == location }

    /** Read by location, exactly as the real store reads it. */
    override suspend fun coverFor(location: String): CoverImage? =
        getDocument(location)?.coverImage

    override suspend fun knownLocations(): Set<String> =
        documents.mapTo(mutableSetOf()) { it.document.location }

    override suspend fun setFavorite(location: String, isFavorite: Boolean) {
        val index = documents.indexOfFirst { it.document.location == location }
        if (index >= 0) {
            documents[index] = documents[index].copy(isFavorite = isFavorite)
        }
    }

    override suspend fun removeDocument(location: String) {
        documents.removeAll { it.document.location == location }
    }
}
