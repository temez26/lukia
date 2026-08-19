package io.lukia.core.infrastructure.archive

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Regression tests for the entry-size cap in [ZipArchiveReader], using a hand-assembled
 * archive (not java.util.zip.ZipOutputStream, which always recomputes correct sizes and
 * can't be made to lie) so the declared size fields can diverge from the real data.
 */
class ZipArchiveReaderSizeCapTest {

    @Test
    fun builderRoundTripsWhenDeclaredSizesAreTruthful() {
        val content = "x".encodeToByteArray()
        val compressed = deflate(content)

        val zipBytes = buildZip(
            name = "small.bin",
            compressionMethod = 8,
            compressedData = compressed,
            compressedSizeField = compressed.size,
            uncompressedSizeField = content.size,
        )

        val result = ZipArchiveReader(zipBytes).readEntry("small.bin")

        assertEquals(content.toList(), result?.toList())
    }

    @Test
    fun readEntryWithImplausibleDeclaredUncompressedSizeReturnsNull() {
        val content = "x".encodeToByteArray()
        val compressed = deflate(content)

        val zipBytes = buildZip(
            name = "huge.bin",
            compressionMethod = 8,
            compressedData = compressed,
            compressedSizeField = compressed.size,
            uncompressedSizeField = 500_000_000,
        )

        assertNull(ZipArchiveReader(zipBytes).readEntry("huge.bin"))
    }

    @Test
    fun readEntryWithNegativeDeclaredCompressedSizeReturnsNull() {
        val content = "x".encodeToByteArray()
        val compressed = deflate(content)

        val zipBytes = buildZip(
            name = "negative.bin",
            compressionMethod = 8,
            compressedData = compressed,
            compressedSizeField = -16,
            uncompressedSizeField = content.size,
        )

        assertNull(ZipArchiveReader(zipBytes).readEntry("negative.bin"))
    }

    private fun deflate(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true)
        deflater.setInput(data)
        deflater.finish()
        val buffer = ByteArray(1024)
        val output = ByteArrayOutputStream()
        while (!deflater.finished()) {
            val count = deflater.deflate(buffer)
            output.write(buffer, 0, count)
        }
        deflater.end()
        return output.toByteArray()
    }

    private fun buildZip(
        name: String,
        compressionMethod: Int,
        compressedData: ByteArray,
        compressedSizeField: Int,
        uncompressedSizeField: Int,
    ): ByteArray {
        val nameBytes = name.encodeToByteArray()

        val localHeader = LittleEndianBuffer().apply {
            u32(0x04034b50) // local file header signature
            u16(20) // version needed to extract
            u16(0) // general purpose bit flag
            u16(compressionMethod)
            u16(0) // last mod file time
            u16(0) // last mod file date
            u32(0) // crc-32
            u32(compressedSizeField)
            u32(uncompressedSizeField)
            u16(nameBytes.size)
            u16(0) // extra field length
            bytes(nameBytes)
        }

        val localHeaderOffset = 0
        val localSectionSize = localHeader.size() + compressedData.size

        val centralHeader = LittleEndianBuffer().apply {
            u32(0x02014b50) // central directory file header signature
            u16(20) // version made by
            u16(20) // version needed to extract
            u16(0) // general purpose bit flag
            u16(compressionMethod)
            u16(0) // last mod file time
            u16(0) // last mod file date
            u32(0) // crc-32
            u32(compressedSizeField)
            u32(uncompressedSizeField)
            u16(nameBytes.size)
            u16(0) // extra field length
            u16(0) // file comment length
            u16(0) // disk number start
            u16(0) // internal file attributes
            u32(0) // external file attributes
            u32(localHeaderOffset)
            bytes(nameBytes)
        }

        val eocd = LittleEndianBuffer().apply {
            u32(0x06054b50) // end of central dir signature
            u16(0) // number of this disk
            u16(0) // disk with the start of the central directory
            u16(1) // total entries on this disk
            u16(1) // total entries
            u32(centralHeader.size())
            u32(localHeaderOffset + localSectionSize)
            u16(0) // .ZIP file comment length
        }

        val output = ByteArrayOutputStream()
        output.write(localHeader.toByteArray())
        output.write(compressedData)
        output.write(centralHeader.toByteArray())
        output.write(eocd.toByteArray())
        return output.toByteArray()
    }

    private class LittleEndianBuffer {
        private val bytes = mutableListOf<Byte>()

        fun u16(value: Int) {
            bytes.add((value and 0xFF).toByte())
            bytes.add(((value shr 8) and 0xFF).toByte())
        }

        fun u32(value: Int) {
            bytes.add((value and 0xFF).toByte())
            bytes.add(((value ushr 8) and 0xFF).toByte())
            bytes.add(((value ushr 16) and 0xFF).toByte())
            bytes.add(((value ushr 24) and 0xFF).toByte())
        }

        fun bytes(value: ByteArray) {
            bytes.addAll(value.toList())
        }

        fun size(): Int = bytes.size

        fun toByteArray(): ByteArray = bytes.toByteArray()
    }
}
