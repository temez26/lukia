package io.lukia.core.infrastructure

/**
 * The whole file at [path], read into memory. Throws if it cannot be read.
 *
 * Whole-file rather than streamed because everything read through here is a
 * container that has to be present in full before any of it means anything: a
 * ZIP's directory is at its end, and a document's text is spread across entries
 * in no particular order. The PDF path deliberately does not come through here -
 * a PDF can be hundreds of megabytes and is read in windows instead.
 *
 * [path] is always a working copy in app storage, which is what lets every
 * platform take a plain file path here.
 */
internal expect fun readFileBytes(path: String): ByteArray

/**
 * Writes [bytes] to [path], replacing whatever was there. Throws if it cannot
 * be written.
 *
 * Whole-file for the same reason [readFileBytes] is: what goes through here is
 * written all at once or not at all, and a half-written one is not a shorter
 * version of it but a file the reader has to recognise as broken.
 */
internal expect fun writeFileBytes(path: String, bytes: ByteArray)

/**
 * How large the file at [path] is, or null when there is no file there.
 *
 * Asked so that something kept beside a document can tell whether the document
 * is still the one it was made from, without reading either of them.
 */
internal expect fun fileSizeBytes(path: String): Long?
