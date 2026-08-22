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
internal actual fun inflateRaw(compressedData: ByteArray, uncompressedSize: Int): ByteArray {
    if (uncompressedSize == 0) return ByteArray(0)

    val output = ByteArray(uncompressedSize)
    var producedSize = 0

    memScoped {
        val stream = alloc<z_stream>()

        val initResult = inflateInit2_(
            stream.ptr,
            -15,
            zlibVersion()?.toKString(),
            sizeOf<z_stream>().toInt(),
        )
        if (initResult != Z_OK) return@memScoped

        compressedData.usePinned { inPinned ->
            output.usePinned { outPinned ->
                stream.next_in = if (compressedData.isEmpty()) null else inPinned.addressOf(0).reinterpret()
                stream.avail_in = compressedData.size.toUInt()
                stream.next_out = outPinned.addressOf(0).reinterpret()
                stream.avail_out = uncompressedSize.toUInt()

                val result = inflate(stream.ptr, 0)
                if (result == Z_OK || result == Z_STREAM_END) {
                    producedSize = uncompressedSize - stream.avail_out.toInt()
                }
            }
        }

        inflateEnd(stream.ptr)
    }

    return if (producedSize == uncompressedSize) output else output.copyOf(producedSize)
}
