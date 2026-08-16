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
