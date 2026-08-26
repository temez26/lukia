package io.lukia.core.infrastructure

/**
 * Names the app's own copy of a document handed to it.
 *
 * Named from the bytes rather than from where they came from, which is what
 * [WorkingCopyNaming] does. A shared document has no stable location to hash -
 * that is the whole reason it is being copied - and the sending app's file name
 * is not one either: two different books arrive as `document.pdf` often enough
 * that trusting it would have one quietly overwrite the other.
 *
 * Hashing the content also means sharing the same book twice lands on the same
 * name, so the second arrival replaces the first copy and updates the shelf's
 * existing entry instead of adding a duplicate beside it.
 */
internal object IncomingDocumentNaming {

    fun fileName(bytes: ByteArray, extension: String): String {
        val suffix = extension.filter { it.isLetterOrDigit() }.lowercase().take(MAX_EXTENSION_LENGTH)
        val prefix = "in-${fnv1aHex(bytes)}"

        return if (suffix.isEmpty()) prefix else "$prefix.$suffix"
    }

    private const val MAX_EXTENSION_LENGTH = 8
}
