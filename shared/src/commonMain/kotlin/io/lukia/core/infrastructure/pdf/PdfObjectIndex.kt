package io.lukia.core.infrastructure.pdf

import io.lukia.core.infrastructure.ByteSource
import io.lukia.core.infrastructure.archive.inflateZlib

/**
 * Finds the objects in a PDF and hands them back on request.
 *
 * Scans the file for object headers rather than following the cross-reference
 * table - what a repair tool does. It costs one pass over the bytes and skips
 * cross-reference streams, their column predictors and the chains of incremental
 * updates: three substantial pieces of the format whose only purpose would be
 * finding offsets this reads directly.
 *
 * It is also what makes a damaged file still give up its chapter list.
 */
internal class PdfObjectIndex(private val source: ByteSource) {

    private val scan = scanPdfObjects(source)

    /** Objects that live inside object streams, parsed when those are expanded. */
    private val streamObjects = HashMap<Int, PdfValue>()

    private val parsed = HashMap<Int, PdfValue?>()

    private var expandedObjectStreams = false

    /**
     * True when the file says its contents are encrypted. Strings would come
     * back as ciphertext, so a chapter list read from one would be gibberish -
     * better to report no outline than a page of mojibake.
     *
     * Read from the newest trailer and only that one, because it is what
     * decides. Two tempting alternatives both take readable books away:
     *
     * Scanning for the bytes `/Encrypt` anywhere in the file - the sequence
     * turns up inside the compressed streams of a long document, and a file
     * whose trailer says nothing about encryption is not encrypted however
     * often those nine bytes appear.
     *
     * Asking whether *any* trailer names an /Encrypt - a file whose encryption
     * was removed keeps the declaring trailer as an earlier revision and
     * appends one that does not. This is how shops produce DRM-free copies of
     * encrypted books, so it is common rather than exotic.
     */
    val isEncrypted: Boolean by lazy {
        currentTrailer()?.get("Encrypt") != null
    }

    /** The document catalog, which everything else hangs from. */
    fun catalog(): PdfValue.Dictionary? {
        trailerDictionaries().forEach { trailer ->
            resolve(trailer["Root"]).asDictionary?.let { return it }
        }

        // Last resort for a file whose trailer is damaged: the catalog is
        // whatever object says it is one.
        return findObjectOfType("Catalog")
    }

    /**
     * The document information dictionary, where a PDF keeps its title, author
     * and the application that produced it.
     *
     * Taken from the newest trailer that names one rather than the first
     * `/Info` anywhere: an incrementally updated file keeps its earlier
     * trailers, and what the reader would recognise is the properties dialog of
     * whatever last saved it.
     *
     * Null for an encrypted file, whose strings would come back as ciphertext -
     * the same reason the outline is not read from one.
     */
    fun info(): PdfValue.Dictionary? {
        if (isEncrypted) return null

        trailerDictionaries().forEach { trailer ->
            resolve(trailer["Info"]).asDictionary?.let { return it }
        }

        return null
    }

    /**
     * The trailer of the file's newest revision, which is the one whose word
     * counts. Recognised by naming a catalog, the same test [catalog] makes -
     * the markers these are found by also match the odd dictionary that is not
     * a trailer at all.
     */
    private fun currentTrailer(): PdfValue.Dictionary? =
        trailerDictionaries().firstOrNull { it["Root"] != null }

    /**
     * Every trailer the file carries, newest first - an incrementally updated
     * file appends one each time it is saved, and the last word is the current
     * one. A file with no classic trailer keeps the same keys on its
     * cross-reference stream instead, whose dictionary is never itself
     * compressed.
     */
    private fun trailerDictionaries(): Sequence<PdfValue.Dictionary> =
        scan.trailerOffsets.asReversed().asSequence().mapNotNull { readTrailerDictionary(it) } +
            scan.crossReferenceStreamOffsets.asReversed().asSequence()
                .mapNotNull { objectAt(it).asDictionary }

    /** Follows indirect references until something concrete comes back. */
    fun resolve(value: PdfValue?): PdfValue? {
        var current = value
        var hops = 0

        while (current is PdfValue.Reference && hops < MAX_REFERENCE_HOPS) {
            current = objectNumbered(current.objectNumber)
            hops++
        }

        return current
    }

    fun objectNumbered(number: Int): PdfValue? {
        parsed[number]?.let { return it }
        if (parsed.containsKey(number)) return null

        val value = readObject(number)
        parsed[number] = value

        return value
    }

    /** Reads a stream's bytes, decoding FlateDecode where that is the filter. */
    fun streamData(stream: PdfValue.Stream): ByteArray? {
        val declaredLength = resolve(stream.dictionary["Length"]).asInt
        val length = declaredLength?.takeIf { it in 1..MAX_STREAM_BYTES }
            ?: measureStreamLength(stream.dataOffset)
            ?: return null

        val raw = source.read(stream.dataOffset, length)
        if (raw.isEmpty()) return null

        val filter = resolve(stream.dictionary["Filter"])
        val filterNames = when (filter) {
            is PdfValue.Name -> listOf(filter.value)
            is PdfValue.Array -> filter.items.mapNotNull { it.asName }
            else -> emptyList()
        }

        return when {
            filterNames.isEmpty() -> raw
            filterNames.any { it == "FlateDecode" || it == "Fl" } ->
                inflateZlib(raw, MAX_DECODED_BYTES)
            // Any other filter - an image codec, an encryption crypt filter -
            // is not something a table of contents is ever stored behind.
            else -> null
        }
    }

    private fun readObject(number: Int): PdfValue? {
        scan.offsets[number]?.let { offset ->
            objectAt(offset)?.let { return it }
        }

        // Not in the file directly, so it is packed inside an object stream.
        if (!expandedObjectStreams) {
            expandObjectStreams()
            scan.offsets[number]?.let { offset -> objectAt(offset)?.let { return it } }
        }

        return streamObjects[number]
    }

    /**
     * Reads the object at [offset], widening the window until the whole object
     * fits. Most are a few hundred bytes; a page tree node listing thousands of
     * children is the reason this can grow.
     */
    private fun objectAt(offset: Long): PdfValue? {
        var window = INITIAL_WINDOW

        while (window <= MAX_WINDOW) {
            val bytes = source.read(offset, window)

            PdfObjectParser(bytes, offset).parseIndirectObject()?.let { return it }

            // The window already reached the end of the file, so there is
            // nothing more to read and the object is simply malformed. An empty
            // read lands here too, having parsed to nothing above it.
            if (bytes.size < window) return null

            window *= WINDOW_GROWTH
        }

        return null
    }

    private fun readTrailerDictionary(offset: Long): PdfValue.Dictionary? {
        val bytes = source.read(offset, TRAILER_WINDOW)
        if (bytes.isEmpty()) return null

        return PdfObjectParser(bytes, offset).parseValue().asDictionary
    }

    /**
     * Unpacks every object stream in the file. Done in one go the first time an
     * object cannot be found directly, because the objects a table of contents
     * needs are almost always packed together in the same one or two streams.
     */
    private fun expandObjectStreams() {
        expandedObjectStreams = true

        scan.objectStreamNumbers.forEach { number ->
            if (streamObjects.size >= MAX_STREAM_OBJECTS) return

            val stream = scan.offsets[number]?.let { objectAt(it) } as? PdfValue.Stream ?: return@forEach
            expandObjectStream(stream)
        }
    }

    private fun expandObjectStream(stream: PdfValue.Stream) {
        val count = resolve(stream.dictionary["N"]).asInt ?: return
        val firstOffset = resolve(stream.dictionary["First"]).asInt ?: return
        val decoded = streamData(stream) ?: return

        if (count <= 0 || firstOffset < 0 || firstOffset > decoded.size) return

        // The stream opens with `objectNumber offset` pairs, one per object,
        // whose offsets are relative to /First.
        val header = PdfObjectParser(decoded.copyOfRange(0, firstOffset))
        val pairs = ArrayList<Pair<Int, Int>>(count)

        repeat(count) {
            val objectNumber = (header.parseValue() as? PdfValue.Number)?.value?.toInt()
            val relativeOffset = (header.parseValue() as? PdfValue.Number)?.value?.toInt()

            if (objectNumber != null && relativeOffset != null) {
                pairs += objectNumber to relativeOffset
            }
        }

        pairs.forEach { (objectNumber, relativeOffset) ->
            val start = firstOffset + relativeOffset
            if (start !in 0..decoded.size) return@forEach
            if (streamObjects.containsKey(objectNumber)) return@forEach

            val value = PdfObjectParser(decoded.copyOfRange(start, decoded.size)).parseValue()
            if (value != null) streamObjects[objectNumber] = value
        }
    }

    /** Falls back to the length up to `endstream` when /Length is unusable. */
    private fun measureStreamLength(dataOffset: Long): Int? {
        val window = source.read(dataOffset, MAX_STREAM_BYTES)
        val marker = "endstream".encodeToByteArray()
        val index = window.indexOfSequence(marker, 0)

        return index?.takeIf { it > 0 }
    }

    private fun findObjectOfType(type: String): PdfValue.Dictionary? {
        if (!expandedObjectStreams) expandObjectStreams()

        streamObjects.values.forEach { value ->
            val dictionary = value.asDictionary
            if (dictionary?.get("Type").asName == type) return dictionary
        }

        scan.offsets.keys.sorted().forEach { number ->
            val dictionary = objectNumbered(number).asDictionary
            if (dictionary?.get("Type").asName == type) return dictionary
        }

        return null
    }

    private companion object {
        const val INITIAL_WINDOW = 4096
        const val WINDOW_GROWTH = 8
        const val MAX_WINDOW = 1 shl 22
        const val TRAILER_WINDOW = 4096
        const val MAX_REFERENCE_HOPS = 16
        const val MAX_STREAM_BYTES = 1 shl 24
        const val MAX_DECODED_BYTES = 1 shl 25
        const val MAX_STREAM_OBJECTS = 100_000
    }
}
