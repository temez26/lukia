package io.lukia.core.infrastructure.archive

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import platform.zlib.Z_NO_FLUSH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2_
import platform.zlib.z_stream
import platform.zlib.zlibVersion

/**
 * Apple's own zlib, driven by hand: Kotlin/Native has no `java.util.zip`, so
 * the stream and its window size are set up here rather than by a library.
 *
 * The window size is negative on purpose - that is how zlib is told the data
 * has no header on it, which is how a ZIP entry stores it.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun inflateRaw(compressedData: ByteArray, uncompressedSize: Int): ByteArray {
    if (uncompressedSize == 0) return ByteArray(0)

    val output = ByteArray(uncompressedSize)
    var producedSize = 0
    var broken = false

    memScoped {
        val stream = alloc<z_stream>()

        val initResult = inflateInit2_(
            stream.ptr,
            -15,
            zlibVersion()?.toKString(),
            sizeOf<z_stream>().toInt(),
        )
        require(initResult == Z_OK) { "zlib would not start: $initResult" }

        compressedData.usePinned { inPinned ->
            output.usePinned { outPinned ->
                stream.next_in = if (compressedData.isEmpty()) null else inPinned.addressOf(0).reinterpret()
                stream.avail_in = compressedData.size.toUInt()
                stream.next_out = outPinned.addressOf(0).reinterpret()
                stream.avail_out = uncompressedSize.toUInt()

                val result = inflate(stream.ptr, Z_NO_FLUSH)

                // Finished, or out of input with the stream still well formed -
                // what a stream cut short answers, and what this used to throw
                // away. Whatever zlib decoded is already in the buffer and is a
                // prefix of the real thing, which is what the JVM's Inflater
                // loop hands back from the same situation.
                //
                // Anything else is not a DEFLATE stream, and has to be told
                // apart from a short one rather than quietly becoming an empty
                // array: `ZipArchiveReader` reads this through a `runCatching`,
                // and on this platform there was never anything to catch.
                if (result == Z_OK || result == Z_STREAM_END) {
                    producedSize = uncompressedSize - stream.avail_out.toInt()
                } else {
                    broken = true
                }
            }
        }

        inflateEnd(stream.ptr)
    }

    require(!broken) { "Not a DEFLATE stream" }

    return if (producedSize == uncompressedSize) output else output.copyOf(producedSize)
}
