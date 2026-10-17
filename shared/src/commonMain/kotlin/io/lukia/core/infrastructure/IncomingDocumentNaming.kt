package io.lukia.core.infrastructure

/**
 * Names the app's own copy of a document handed to it.
 *
 * Named from the bytes rather than the location, which is [WorkingCopyNaming]'s
 * job: a shared document has no stable location to hash - that is why it is
 * being copied - and the sender's file name is no better, since two different
 * books arrive as `document.pdf` often enough that one would overwrite the other.
 *
 * Hashing the content also means the same book shared twice lands on the same
 * name, replacing the first copy rather than shelving a duplicate.
 */
internal object IncomingDocumentNaming {

    fun fileName(bytes: ByteArray, extension: String): String =
        named("in-${fnv1aHex(bytes)}", extension)

    /**
     * A book the reader picked that arrived as a copy the system will not keep,
     * named by [id] rather than its bytes so it can be moved into place without
     * being read - see `IOSIncomingDocumentStore.adopt`.
     */
    fun pickedName(id: String, extension: String): String =
        named("pick-${id.filter { it.isLetterOrDigit() }.lowercase()}", extension)

    /**
     * A book downloaded from the sync server, named by its content hash so the
     * name is known before the download starts - see `BookFileStore`. The
     * prefix keeps it from ever meeting a shared copy's name.
     */
    fun serverBookName(contentHash: String, extension: String): String =
        named("srv-${contentHash.filter { it.isLetterOrDigit() }}", extension)

    private fun named(prefix: String, extension: String): String {
        val suffix = extension.filter { it.isLetterOrDigit() }.lowercase().take(MAX_EXTENSION_LENGTH)

        return if (suffix.isEmpty()) prefix else "$prefix.$suffix"
    }

    private const val MAX_EXTENSION_LENGTH = 8
}
