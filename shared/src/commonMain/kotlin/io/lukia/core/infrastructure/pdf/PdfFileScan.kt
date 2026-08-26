package io.lukia.core.infrastructure.pdf

/**
 * Where everything sits in a PDF, as found by one pass over its bytes.
 *
 * Separate from [PdfObjectIndex] because finding objects and handing them back
 * are different jobs: this one only ever reads bytes and records offsets, and
 * knows nothing about what a PDF object means.
 */
internal class PdfFileScan(
    /** Object number to where its header sits in the file. */
    val offsets: Map<Int, Long>,
    /** Numbers of the objects that carry an `/ObjStm` stream. */
    val objectStreamNumbers: List<Int>,
    /** Offsets just past each `trailer` keyword, oldest first. */
    val trailerOffsets: List<Long>,
    /** Offsets of the objects carrying an `/XRef` stream, oldest first. */
    val crossReferenceStreamOffsets: List<Long>,
)

/**
 * One pass over the file, collecting where every object starts and where the few
 * landmarks the reader needs sit. Read in windows so that a large book is never
 * held in memory at once.
 */
internal fun scanPdfObjects(source: PdfByteSource): PdfFileScan {
    val objectMarker = "obj".encodeToByteArray()
    val trailerMarker = "trailer".encodeToByteArray()
    val objectStreamMarker = "/ObjStm".encodeToByteArray()
    val crossReferenceMarker = "/XRef".encodeToByteArray()

    val offsets = HashMap<Int, Long>()
    val trailerOffsets = mutableListOf<Long>()
    val objectStreamHits = mutableListOf<Long>()
    val crossReferenceHits = mutableListOf<Long>()

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
            crossReferenceHits += windowStart + index
        }

        if (bytes.size < SCAN_WINDOW) break

        // Overlapped so a header split across two windows is still seen whole
        // by one of them.
        windowStart += SCAN_WINDOW - SCAN_OVERLAP
    }

    // A `/ObjStm` sits inside the object that declares it, so the stream's own
    // number is that of the nearest header before it. The same is true of a
    // cross-reference stream's dictionary.
    val sortedOffsets = offsets.entries.sortedBy { it.value }

    return PdfFileScan(
        offsets = offsets,
        objectStreamNumbers = objectStreamHits.mapNotNull { hit ->
            sortedOffsets.lastOrNull { it.value < hit }?.key
        },
        trailerOffsets = trailerOffsets,
        crossReferenceStreamOffsets = crossReferenceHits.mapNotNull { hit ->
            sortedOffsets.lastOrNull { it.value < hit }?.value
        },
    )
}

/**
 * Walks back from an `obj` keyword over the generation and object numbers that
 * precede it, returning the object's number and where its header begins. Null
 * when what precedes is not an object header at all - the letters also occur
 * inside `endobj` and in ordinary words.
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
    while (index >= 0 && bytes[index] in ZERO..NINE) index--
    if (index == generationEnd) return null

    if (index < 0 || !isWhitespace(bytes[index])) return null
    while (index >= 0 && isWhitespace(bytes[index])) index--

    val numberEnd = index
    while (index >= 0 && bytes[index] in ZERO..NINE) index--
    if (index == numberEnd) return null

    val number = bytes.decodeToString(index + 1, numberEnd + 1).toIntOrNull() ?: return null

    return number to (index + 1)
}

private const val SCAN_WINDOW = 1 shl 20
private const val SCAN_OVERLAP = 64
