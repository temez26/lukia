package io.lukia.core.infrastructure.archive

/**
 * Inflates a raw (headerless) DEFLATE stream, as used inside ZIP entries.
 *
 * Returns fewer bytes than [uncompressedSize] when the stream ends early, so a
 * caller that does not know the decoded size can pass a ceiling and read the
 * real length from what comes back. A stream cut short is not a failure: what
 * was decoded is a prefix of the real thing, and a chapter of a damaged book
 * beats nothing.
 *
 * Throws when the data is not a DEFLATE stream, which must stay distinguishable
 * from that: every caller wraps this in `runCatching`, which means nothing on a
 * platform that answers rubbish with an empty array. Stated here rather than
 * left to each implementation because it is the half the three had drifted on.
 */
expect fun inflateRaw(compressedData: ByteArray, uncompressedSize: Int): ByteArray

/**
 * Inflates a zlib stream - DEFLATE wrapped in the two-byte header and trailing
 * checksum of RFC 1950, which is what PDF's FlateDecode filter produces.
 *
 * [maxOutputSize] is a ceiling rather than an expected size: PDF does not record
 * how large a stream decodes to, so the caller bounds the work and gets back
 * however much was there.
 *
 * Returns null when the data is not a stream this can read.
 */
fun inflateZlib(compressedData: ByteArray, maxOutputSize: Int): ByteArray? {
    if (compressedData.size < ZLIB_HEADER_SIZE || maxOutputSize <= 0) return null

    val method = compressedData[0].toInt() and 0x0F
    val checkBits = ((compressedData[0].toInt() and 0xFF) shl 8) or
        (compressedData[1].toInt() and 0xFF)

    // A zlib header is a deflate method plus a header checksum that is a
    // multiple of 31. Anything else is either a raw stream written by a
    // careless producer, or not deflate at all - try it raw and let it fail.
    val body = if (method == DEFLATE_METHOD && checkBits % 31 == 0) {
        compressedData.copyOfRange(ZLIB_HEADER_SIZE, compressedData.size)
    } else {
        compressedData
    }

    return runCatching { inflateUpTo(body, maxOutputSize) }
        .getOrNull()
        ?.takeIf { it.isNotEmpty() }
}

/**
 * Inflates into a buffer sized for the stream rather than for the ceiling,
 * widening it for as long as what came back filled it.
 *
 * [inflateRaw] allocates the whole of the size it is given before it decodes a
 * byte, and a PDF's ceiling is tens of megabytes where its streams are a few
 * kilobytes. A file with a thousand object streams was a thousand such buffers,
 * and on a phone with a book already in memory each one failed to allocate -
 * which is a book that never finished opening.
 */
private fun inflateUpTo(body: ByteArray, maxOutputSize: Int): ByteArray {
    var ceiling = maxOf(body.size.toLong() * FIRST_GUESS, SMALLEST_GUESS.toLong())
        .coerceAtMost(maxOutputSize.toLong())
        .toInt()

    while (true) {
        val inflated = inflateRaw(body, ceiling)

        // Short of the buffer is the end of the stream. Full may be the end or
        // may be where the buffer stopped it, and only more room can say.
        if (inflated.size < ceiling || ceiling >= maxOutputSize) return inflated

        ceiling = (ceiling.toLong() * GUESS_GROWTH).coerceAtMost(maxOutputSize.toLong()).toInt()
    }
}

private const val ZLIB_HEADER_SIZE = 2
private const val DEFLATE_METHOD = 8

/** How many times its own size a stream is first assumed to decode to. */
private const val FIRST_GUESS = 4

/** Below this a guess saves nothing worth a second pass. */
private const val SMALLEST_GUESS = 16 * 1024

/** Four rather than two: a stream that outgrew one guess usually outgrows the next. */
private const val GUESS_GROWTH = 4
