package io.lukia.core.infrastructure

/**
 * SHA-256, fed a piece at a time: the platform's own where it has one - the
 * JDK's `MessageDigest` on Android and the desktop - and [PortableSha256]
 * where it does not.
 *
 * The hash is a book's identity across devices - see
 * docs/decisions/0002-content-hash-book-identity.md - so it has to be the same
 * hash on every platform, and the server's (`MessageDigest`) above all.
 * `Sha256AgreementTest` holds [PortableSha256] to `MessageDigest`, which is what
 * keeps the platforms using it in step with the rest.
 *
 * The platform's where possible because a whole library is hashed the first
 * time a device signs in, and the JDK's is about six times faster than the
 * portable one on the JVM - 29 ms against 172 ms for 64 MB.
 */
expect class Sha256() {
    fun update(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset)

    fun digest(): ByteArray
}

/**
 * SHA-256 as FIPS 180-4 defines it, in common code: one implementation tested
 * once, for the platforms with no SHA-256 of their own to hand.
 *
 * Not constant-time and not meant to be: it names files, it guards nothing.
 */
class PortableSha256 {

    private val state = InitialState.copyOf()
    private val block = ByteArray(BlockBytes)
    private var blockFill = 0
    private var totalBytes = 0L
    private val schedule = IntArray(ScheduleWords)

    fun update(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset) {
        var index = offset
        val end = offset + length
        totalBytes += length

        while (index < end) {
            val count = minOf(BlockBytes - blockFill, end - index)
            bytes.copyInto(block, blockFill, index, index + count)
            blockFill += count
            index += count

            if (blockFill == BlockBytes) {
                compress()
                blockFill = 0
            }
        }
    }

    /** The 32-byte digest. The instance is spent afterwards. */
    fun digest(): ByteArray {
        val bitLength = totalBytes * Byte.SIZE_BITS

        block[blockFill++] = PaddingMarker
        if (blockFill > BlockBytes - LengthBytes) {
            block.fill(0, blockFill)
            compress()
            blockFill = 0
        }
        block.fill(0, blockFill, BlockBytes - LengthBytes)
        for (i in 0 until LengthBytes) {
            block[BlockBytes - 1 - i] = (bitLength ushr (i * Byte.SIZE_BITS)).toByte()
        }
        compress()

        return ByteArray(DigestBytes) { i -> (state[i / Int.SIZE_BYTES] ushr (24 - (i % Int.SIZE_BYTES) * 8)).toByte() }
    }

    private fun compress() {
        for (t in 0 until BlockWords) {
            val i = t * Int.SIZE_BYTES
            schedule[t] = (block[i].toInt() and 0xFF shl 24) or
                (block[i + 1].toInt() and 0xFF shl 16) or
                (block[i + 2].toInt() and 0xFF shl 8) or
                (block[i + 3].toInt() and 0xFF)
        }
        for (t in BlockWords until ScheduleWords) {
            val s0 = schedule[t - 15].rotateRight(7) xor schedule[t - 15].rotateRight(18) xor (schedule[t - 15] ushr 3)
            val s1 = schedule[t - 2].rotateRight(17) xor schedule[t - 2].rotateRight(19) xor (schedule[t - 2] ushr 10)
            schedule[t] = schedule[t - 16] + s0 + schedule[t - 7] + s1
        }

        var a = state[0]
        var b = state[1]
        var c = state[2]
        var d = state[3]
        var e = state[4]
        var f = state[5]
        var g = state[6]
        var h = state[7]

        for (t in 0 until ScheduleWords) {
            val sum1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
            val choice = (e and f) xor (e.inv() and g)
            val temp1 = h + sum1 + choice + RoundConstants[t] + schedule[t]
            val sum0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
            val majority = (a and b) xor (a and c) xor (b and c)
            val temp2 = sum0 + majority

            h = g
            g = f
            f = e
            e = d + temp1
            d = c
            c = b
            b = a
            a = temp1 + temp2
        }

        state[0] += a
        state[1] += b
        state[2] += c
        state[3] += d
        state[4] += e
        state[5] += f
        state[6] += g
        state[7] += h
    }

    private companion object {
        const val BlockBytes = 64
        const val BlockWords = 16
        const val ScheduleWords = 64
        const val LengthBytes = 8
        const val DigestBytes = 32
        const val PaddingMarker: Byte = -0x80

        val InitialState = intArrayOf(
            0x6a09e667,
            -0x4498517b,
            0x3c6ef372,
            -0x5ab00ac6,
            0x510e527f,
            -0x64fa9774,
            0x1f83d9ab,
            0x5be0cd19,
        )

        val RoundConstants = intArrayOf(
            0x428a2f98, 0x71374491, -0x4a3f0431, -0x164a245b, 0x3956c25b, 0x59f111f1, -0x6dc07d5c, -0x54e3a12b,
            -0x27f85568, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, -0x7f214e02, -0x6423f959, -0x3e640e8c,
            -0x1b64963f, -0x1041b87a, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
            -0x67c1aeae, -0x57ce3993, -0x4ffcd838, -0x40a68039, -0x391ff40d, -0x2a586eb9, 0x06ca6351, 0x14292967,
            0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, -0x7e3d36d2, -0x6d8dd37b,
            -0x5d40175f, -0x57e599b5, -0x3db47490, -0x3893ae5d, -0x2e6d17e7, -0x2966f9dc, -0xbf1ca7b, 0x106aa070,
            0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
            0x748f82ee, 0x78a5636f, -0x7b3787ec, -0x7338fdf8, -0x6f410006, -0x5baf9315, -0x41065c09, -0x398e870e,
        )
    }
}

/**
 * The SHA-256 of everything [this] holds, as lower-case hex - a book's id.
 *
 * Read a window at a time, so a book of any size costs one window of memory.
 */
fun ByteSource.sha256Hex(windowBytes: Int = DefaultWindowBytes): String {
    val hash = Sha256()
    var offset = 0L
    while (offset < size) {
        val window = read(offset, windowBytes)
        if (window.isEmpty()) break
        hash.update(window)
        offset += window.size
    }
    return hash.digest().toHex()
}

fun ByteArray.toHex(): String = joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

private const val DefaultWindowBytes = 1024 * 1024
