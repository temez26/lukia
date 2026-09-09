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
 *
 * [windowBytes] is a parameter only so a test can set it small enough to put an
 * object header across a boundary - the case the overlap below exists for, which
 * at the real size would need a megabyte of fixture.
 */
internal fun scanPdfObjects(source: PdfByteSource, windowBytes: Int = SCAN_WINDOW): PdfFileScan {
    require(windowBytes > SCAN_OVERLAP) { "a window has to be longer than the overlap it keeps" }

    val objectMarker = "obj".encodeToByteArray()
    val trailerMarker = "trailer".encodeToByteArray()
    val objectStreamMarker = "/ObjStm".encodeToByteArray()
    val crossReferenceMarker = "/XRef".encodeToByteArray()

    val offsets = HashMap<Int, Long>()
    val trailerOffsets = mutableListOf<Long>()
    val objectStreamHits = mutableListOf<Long>()
    val crossReferenceHits = mutableListOf<Long>()

    var windowStart = 0L

    // A window shorter than one asked for is the last of the file; an empty one
    // is a file that ended exactly between two. Read as the loop's own condition
    // rather than as breaks inside it, so there is one way out.
    var reading = true

    while (reading && windowStart < source.size) {
        val bytes = source.read(windowStart, windowBytes)
        reading = bytes.size == windowBytes

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

        // Overlapped so a header split across two windows is still seen whole
        // by one of them.
        windowStart += windowBytes - SCAN_OVERLAP
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
    if (bytes.endsEndobjAt(objIndex - 1)) return null

    val header = HeaderScan(bytes, from = objIndex - 1)

    // `<number> <generation> obj`, so read backwards it is the generation first
    // and the object's own number second. Both have to be there, and the second
    // is only looked for once the first was found.
    val hasGeneration = header.skipNumberBefore()
    val hasNumber = hasGeneration && header.skipNumberBefore()

    if (!hasNumber) return null

    return header.numberAndStart()
}

/** `endobj` ends in the same three letters as `obj` does. */
private fun ByteArray.endsEndobjAt(index: Int): Boolean {
    if (index < 2) return false

    return this[index] == 'd'.code.toByte() &&
        this[index - 1] == 'n'.code.toByte() &&
        this[index - 2] == 'e'.code.toByte()
}

/**
 * A cursor walking backwards over the `<whitespace><digits>` pairs that precede
 * an `obj` keyword, remembering where the last run of digits sat.
 *
 * Backwards because the keyword is what the scan finds; the numbers before it are
 * only reachable from there, and how many digits they have is not known until the
 * walk runs off the front of them.
 */
private class HeaderScan(private val bytes: ByteArray, from: Int) {

    private var index = from
    private var digitsStart = 0
    private var digitsEnd = 0

    /**
     * False as soon as what precedes the cursor is not whitespace and then
     * digits - and false too when those digits reach the front of the window,
     * because then there is no telling how many more of them there were.
     *
     * The second case costs a book. A boundary landing inside `11 0 obj` leaves
     * the next window reading `1 0 obj` - a well-formed header for a different
     * object at a later offset, and later wins, so the catalog of a file long
     * enough for two windows would be replaced by the tail of an unrelated one.
     * Nothing is lost by refusing: the previous window overlaps by more than a
     * header's length and has already recorded the object from its start.
     */
    fun skipNumberBefore(): Boolean {
        if (index < 0 || !isWhitespace(bytes[index])) return false

        while (index >= 0 && isWhitespace(bytes[index])) index--

        digitsEnd = index
        while (index >= 0 && bytes[index] in ZERO..NINE) index--
        digitsStart = index + 1

        return index >= 0 && index != digitsEnd
    }

    /** The digits last walked over, with where they begin in the file. */
    fun numberAndStart(): Pair<Int, Int>? =
        bytes.decodeToString(digitsStart, digitsEnd + 1)
            .toIntOrNull()
            ?.let { it to digitsStart }
}

private const val SCAN_WINDOW = 1 shl 20
private const val SCAN_OVERLAP = 64
