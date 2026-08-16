package io.lukia.core.infrastructure.archive

import java.util.zip.Inflater

internal actual fun inflateRaw(compressedData: ByteArray, uncompressedSize: Int): ByteArray {
    val inflater = Inflater(true)
    try {
        inflater.setInput(compressedData)
        val output = ByteArray(uncompressedSize)
        var totalWritten = 0
        while (totalWritten < uncompressedSize && !inflater.finished()) {
            val written = inflater.inflate(output, totalWritten, uncompressedSize - totalWritten)
            if (written == 0 && inflater.needsInput()) break
            totalWritten += written
        }
        return if (totalWritten == uncompressedSize) output else output.copyOf(totalWritten)
    } finally {
        inflater.end()
    }
}
