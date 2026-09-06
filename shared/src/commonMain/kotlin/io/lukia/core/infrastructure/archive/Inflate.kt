package io.lukia.core.infrastructure.archive

/**
 * Inflates a raw (headerless) DEFLATE stream, as used inside ZIP entries.
 *
 * Returns fewer bytes than [uncompressedSize] when the stream ends early, so a
 * caller that does not know the decoded size up front can pass a ceiling and
 * read the real length from what comes back. A stream cut short is not a
 * failure: what was decoded before it ran out is a prefix of the real thing, and
 * a chapter of a damaged book is worth more to a reader than nothing at all.
 *
 * Throws when the data is not a DEFLATE stream, which is a different thing and
 * has to stay distinguishable from the first: every caller here wraps this in
 * `runCatching`, and that wrapping means nothing on a platform that answers a
 * stream of rubbish with an empty array. Said here rather than left to each
 * implementation because it is the half the three of them had drifted on.
 */
internal expect fun inflateRaw(compressedData: ByteArray, uncompressedSize: Int): ByteArray

/**
 * Inflates a zlib stream - DEFLATE wrapped in the two-byte header and trailing
 * checksum of RFC 1950, which is what PDF's FlateDecode filter produces.
 *
 * [maxOutputSize] is a ceiling rather than an expected size: PDF does not record
 * how large a stream decodes to, so the caller bounds the work instead and gets
 * back however much was really there.
 *
 * Returns null when the data is not a stream this can read.
 */
internal fun inflateZlib(compressedData: ByteArray, maxOutputSize: Int): ByteArray? {
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

    return runCatching { inflateRaw(body, maxOutputSize) }
        .getOrNull()
        ?.takeIf { it.isNotEmpty() }
}

private const val ZLIB_HEADER_SIZE = 2
private const val DEFLATE_METHOD = 8
