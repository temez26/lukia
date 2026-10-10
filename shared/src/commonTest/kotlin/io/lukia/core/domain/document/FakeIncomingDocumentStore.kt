package io.lukia.core.domain.document

/**
 * Stands in for the platform stores, which all need a real filesystem.
 *
 * Reads the document the way a real one does - that read is the part with a
 * deadline on it, so a test that skipped it would not be exercising the thing
 * worth exercising - and hands back a location under a directory that does not
 * exist, which is enough for everything downstream of the copy.
 */
class FakeIncomingDocumentStore(
    /** Set to have the store fail the way an unreadable document does. */
    var failing: Boolean = false,
) : IncomingDocumentStore {

    val kept = mutableListOf<String>()
    val discarded = mutableListOf<String>()

    override suspend fun keep(document: IncomingDocument): PickedDocument {
        require(!failing) { "Could not read ${document.fileName}" }

        val bytes = document.read()
        requireNotNull(bytes) { "Could not read ${document.fileName}" }

        kept.add(document.fileName)

        return PickedDocument(
            fileName = document.fileName,
            extension = document.extension,
            location = "/incoming/${document.fileName}",
        )
    }

    /** Records the copies a real store would delete, and ignores everything else. */
    override suspend fun discard(location: String) {
        if (location.startsWith("/incoming/")) discarded.add(location)
    }
}
