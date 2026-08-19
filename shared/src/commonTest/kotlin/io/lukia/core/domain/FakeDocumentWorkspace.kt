package io.lukia.core.domain

/**
 * Stands in for the platform workspaces, which all need a real filesystem.
 * Records what was copied and what was discarded so tests can assert on it
 * without going near one.
 */
class FakeDocumentWorkspace : DocumentWorkspace {

    val copied = mutableListOf<String>()
    val discarded = mutableListOf<String>()

    override suspend fun workingCopy(document: DocumentInfo): WorkingCopy {
        val location = document.document.location
        copied.add(location)

        return WorkingCopy(
            sourceLocation = location,
            path = "/workspace/${document.document.fileName}",
        )
    }

    override suspend fun discard(location: String) {
        discarded.add(location)
    }
}
