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
 * NOTE: uses Apple's system zlib via cinterop for raw (headerless) DEFLATE
 * inflation, since Kotlin/Native has no equivalent to java.util.zip.Inflater.
 * This has not been compiled/verified on macOS - please build and exercise
 * EPUB cover extraction on device/simulator before relying on it.
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
