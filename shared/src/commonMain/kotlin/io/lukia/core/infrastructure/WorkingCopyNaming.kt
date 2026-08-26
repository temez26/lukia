package io.lukia.core.infrastructure

/**
 * Names the on-disk working copy of a document.
 *
 * A library location is an absolute path, a content:// URI or a file:// URL,
 * none of which survive being used as a file name. The name is derived from a
 * hash of the location instead, which also keeps it stable: reopening a book
 * finds the copy made last time rather than writing a second one.
 *
 * Shared by every platform workspace so that a copy written by one lookup is
 * always found by the next.
 */
internal object WorkingCopyNaming {

    /**
     * The part of the name that identifies the document. Discarding matches on
     * this rather than the full file name, so a copy can be deleted knowing
     * only the location it came from.
     */
    fun prefix(location: String): String = "doc-${hash(location)}"

    fun fileName(location: String, extension: String): String {
        val suffix = extension.filter { it.isLetterOrDigit() }.lowercase().take(MAX_EXTENSION_LENGTH)
        val prefix = prefix(location)

        return if (suffix.isEmpty()) prefix else "$prefix.$suffix"
    }

    private fun hash(value: String): String = fnv1aHex(value)

    private const val MAX_EXTENSION_LENGTH = 8
}
