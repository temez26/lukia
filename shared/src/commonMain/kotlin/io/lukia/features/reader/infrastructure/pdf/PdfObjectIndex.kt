package io.lukia.features.reader.infrastructure.pdf

import io.lukia.core.infrastructure.archive.inflateZlib

/**
 * Finds the objects in a PDF and hands them back on request.
 *
 * Works by scanning the file for object headers rather than by following the
 * cross-reference table. That is the same thing a repair tool does, and it is
 * the right trade here: it costs one pass over the bytes and in exchange skips
 * cross-reference streams, their column predictors, and the chains of
 * incremental updates - three substantial pieces of the format whose only
 * purpose would be finding offsets this reads directly.
 *
 * It is also what makes a damaged file still give up its chapter list.
 */
internal class PdfObjectIndex(private val source: PdfByteSource) {

    /** Object number to where its header sits in the file. */
    private val offsets = HashMap<Int, Long>()

    /** Objects that live inside object streams, parsed when those are expanded. */
    private val streamObjects = HashMap<Int, PdfValue>()

    private val parsed = HashMap<Int, PdfValue?>()
    private val objectStreamNumbers = mutableListOf<Int>()
    private val trailerOffsets = mutableListOf<Long>()
    private val crossReferenceStreamOffsets = mutableListOf<Long>()

    private var expandedObjectStreams = false

    /**
     * True when the file says its contents are encrypted. Strings would come
     * back as ciphertext, so a chapter list read from one would be gibberish -
     * better to report no outline than a page of mojibake.
     */
    var isEncrypted: Boolean = false
        private set

    init {
        scan()
    }

    /** The document catalog, which everything else hangs from. */
    fun catalog(): PdfValue.Dictionary? {
        // The trailer names the catalog directly. Later trailers win: an
        // incrementally updated file appends a new one each time it is saved.
        trailerOffsets.asReversed().forEach { offset ->
            readTrailerDictionary(offset)?.let { trailer ->
                resolve(trailer["Root"]).asDictionary?.let { return it }
            }
        }

        // A file with no classic trailer keeps /Root on its cross-reference
        // stream instead, whose dictionary is never itself compressed.
        crossReferenceStreamOffsets.asReversed().forEach { offset ->
            objectAt(offset).asDictionary?.let { dictionary ->
                resolve(dictionary["Root"]).asDictionary?.let { return it }
            }
        }

        // Last resort for a file whose trailer is damaged: the catalog is
        // whatever object says it is one.
        return findObjectOfType("Catalog")
    }

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
        offsets[number]?.let { offset ->
            objectAt(offset)?.let { return it }
        }

        // Not in the file directly, so it is packed inside an object stream.
        if (!expandedObjectStreams) {
            expandObjectStreams()
            offsets[number]?.let { offset -> objectAt(offset)?.let { return it } }
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
            if (bytes.isEmpty()) return null

            PdfObjectParser(bytes, offset).parseIndirectObject()?.let { return it }

            // The window already reached the end of the file, so there is
            // nothing more to read and the object is simply malformed.
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

        objectStreamNumbers.forEach { number ->
            if (streamObjects.size >= MAX_STREAM_OBJECTS) return

            val stream = offsets[number]?.let { objectAt(it) } as? PdfValue.Stream ?: return@forEach
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

        offsets.keys.sorted().forEach { number ->
            val dictionary = objectNumbered(number).asDictionary
            if (dictionary?.get("Type").asName == type) return dictionary
        }

        return null
    }

    /**
     * One pass over the file, collecting where every object starts and where the
     * few landmarks the reader needs sit. Read in windows so that a large book
     * is never held in memory at once.
     */
    private fun scan() {
        val objectMarker = "obj".encodeToByteArray()
        val trailerMarker = "trailer".encodeToByteArray()
        val objectStreamMarker = "/ObjStm".encodeToByteArray()
        val crossReferenceMarker = "/XRef".encodeToByteArray()
        val encryptMarker = "/Encrypt".encodeToByteArray()

        val objectStreamHits = mutableListOf<Long>()
        var windowStart = 0L

        while (windowStart < source.size) {
            val bytes = source.read(windowStart, SCAN_WINDOW)
            if (bytes.isEmpty()) break

            bytes.forEachSequence(objectMarker) { index ->
                readObjectHeaderStart(bytes, index)?.let { (number, headerStart) ->
                    val offset = windowStart + headerStart
                    // Later wins: an updated file appends replacement objects.
                    if (offsets[number]?.let { offset > it } != false) {
                        offsets[number] = offset
                    }
                }
            }

            bytes.forEachSequence(trailerMarker) { index ->
                trailerOffsets += windowStart + index + trailerMarker.size
            }

            bytes.forEachSequence(objectStreamMarker) { index ->
                objectStreamHits += windowStart + index
            }

            bytes.forEachSequence(crossReferenceMarker) { index ->
                crossReferenceStreamOffsets += windowStart + index
            }

            if (!isEncrypted && bytes.indexOfSequence(encryptMarker, 0) != null) {
                isEncrypted = true
            }

            if (bytes.size < SCAN_WINDOW) break

            // Overlapped so a header split across two windows is still seen
            // whole by one of them.
            windowStart += SCAN_WINDOW - SCAN_OVERLAP
        }

        // A `/ObjStm` sits inside the object that declares it, so the stream's
        // own number is that of the nearest header before it.
        val sortedOffsets = offsets.entries.sortedBy { it.value }
        objectStreamHits.forEach { hit ->
            val owner = sortedOffsets.lastOrNull { it.value < hit }
            if (owner != null) objectStreamNumbers += owner.key
        }

        // The same is true of a cross-reference stream's dictionary.
        val crossReferenceObjects = crossReferenceStreamOffsets.mapNotNull { hit ->
            sortedOffsets.lastOrNull { it.value < hit }?.value
        }
        crossReferenceStreamOffsets.clear()
        crossReferenceStreamOffsets += crossReferenceObjects
    }

    /**
     * Walks back from an `obj` keyword over the generation and object numbers
     * that precede it, returning the object's number and where its header
     * begins. Null when what precedes is not an object header at all - the
     * letters also occur inside `endobj` and in ordinary words.
     */
    private fun readObjectHeaderStart(bytes: ByteArray, objIndex: Int): Pair<Int, Int>? {
        var index = objIndex - 1

        // `endobj` ends in the same three letters.
        if (index >= 2 &&
            bytes[index] == 'd'.code.toByte() &&
            bytes[index - 1] == 'n'.code.toByte() &&
            bytes[index - 2] == 'e'.code.toByte()
        ) {
            return null
        }

        if (index < 0 || !isWhitespace(bytes[index])) return null
        while (index >= 0 && isWhitespace(bytes[index])) index--

        val generationEnd = index
        while (index >= 0 && bytes[index] in DIGIT_RANGE) index--
        if (index == generationEnd) return null

        if (index < 0 || !isWhitespace(bytes[index])) return null
        while (index >= 0 && isWhitespace(bytes[index])) index--

        val numberEnd = index
        while (index >= 0 && bytes[index] in DIGIT_RANGE) index--
        if (index == numberEnd) return null

        val number = bytes.decodeToString(index + 1, numberEnd + 1).toIntOrNull() ?: return null

        return number to (index + 1)
    }

    private companion object {
        val DIGIT_RANGE = 0x30.toByte()..0x39.toByte()

        const val SCAN_WINDOW = 1 shl 20
        const val SCAN_OVERLAP = 64
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

/** Index of the first occurrence of [sequence] at or after [from], or null. */
internal fun ByteArray.indexOfSequence(sequence: ByteArray, from: Int): Int? {
    if (sequence.isEmpty() || sequence.size > size) return null

    outer@ for (start in from..size - sequence.size) {
        for (index in sequence.indices) {
            if (this[start + index] != sequence[index]) continue@outer
        }
        return start
    }

    return null
}

private inline fun ByteArray.forEachSequence(sequence: ByteArray, action: (Int) -> Unit) {
    var from = 0

    while (true) {
        val index = indexOfSequence(sequence, from) ?: return
        action(index)
        from = index + 1
    }
}
