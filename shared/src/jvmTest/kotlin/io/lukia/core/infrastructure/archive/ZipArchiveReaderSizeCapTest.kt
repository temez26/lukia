package io.lukia.core.infrastructure.archive

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Regression tests for the size caps in [ZipArchiveReader], using a hand-assembled
 * archive (not java.util.zip.ZipOutputStream, which always recomputes correct sizes and
 * can't be made to lie) so the declared size fields can diverge from the real data.
 *
 * Two caps, and the second is the one that matters. A per-entry limit stops one lying
 * size field; it does nothing about an archive of two hundred entries that are each
 * just under it, which is what a book being read chapter by chapter would inflate all
 * at once.
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

    /**
     * Each entry is inside the per-entry cap and the archive is not: nothing is read out
     * of it at all, including the small entry that would have been fine on its own.
     */
    @Test
    fun readEntryOnAnArchiveDeclaringMoreThanTheWholeCapReturnsNull() {
        val content = "x".encodeToByteArray()
        val compressed = deflate(content)

        val entries = (0 until 20).map { index ->
            ZipEntrySpec(
                name = "chapter-$index.xhtml",
                compressedData = compressed,
                uncompressedSizeField = 60 * 1024 * 1024,
            )
        } + ZipEntrySpec(
            name = "small.bin",
            compressedData = compressed,
            uncompressedSizeField = content.size,
        )

        val reader = ZipArchiveReader(buildZip(entries))

        assertNull(reader.readEntry("chapter-0.xhtml"))
        assertNull(reader.readEntry("small.bin"))
    }

    /** An archive of the same shape that stays under the cap is read as normal. */
    @Test
    fun readEntryOnAnArchiveOfManyHonestEntriesStillWorks() {
        val content = "x".encodeToByteArray()
        val compressed = deflate(content)

        val entries = (0 until 20).map { index ->
            ZipEntrySpec(
                name = "chapter-$index.xhtml",
                compressedData = compressed,
                uncompressedSizeField = content.size,
            )
        }

        val reader = ZipArchiveReader(buildZip(entries))

        assertEquals(content.toList(), reader.readEntry("chapter-7.xhtml")?.toList())
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

    private class ZipEntrySpec(
        val name: String,
        val compressedData: ByteArray,
        val uncompressedSizeField: Int,
    )

    /**
     * An archive of several deflated entries, laid out one after another with a central
     * directory over them. Only the declared uncompressed size is worth varying here -
     * the whole-archive cap is read off the directory, so the entries need only be
     * well-formed enough to be indexed.
     */
    private fun buildZip(entries: List<ZipEntrySpec>): ByteArray {
        val output = ByteArrayOutputStream()
        val central = ByteArrayOutputStream()
        var offset = 0

        entries.forEach { entry ->
            val localHeader = entry.localHeader()

            central.write(entry.centralHeader(offset))
            output.write(localHeader)
            output.write(entry.compressedData)
            offset += localHeader.size + entry.compressedData.size
        }

        val centralBytes = central.toByteArray()

        output.write(centralBytes)
        output.write(endOfCentralDirectory(entries.size, centralBytes.size, offset))

        return output.toByteArray()
    }

    private fun ZipEntrySpec.localHeader(): ByteArray {
        val nameBytes = name.encodeToByteArray()

        return LittleEndianBuffer().apply {
            u32(0x04034b50)
            u16(20)
            u16(0)
            u16(8)
            u16(0)
            u16(0)
            u32(0)
            u32(compressedData.size)
            u32(uncompressedSizeField)
            u16(nameBytes.size)
            u16(0)
            bytes(nameBytes)
        }.toByteArray()
    }

    private fun ZipEntrySpec.centralHeader(offset: Int): ByteArray {
        val nameBytes = name.encodeToByteArray()

        return LittleEndianBuffer().apply {
            u32(0x02014b50)
            u16(20)
            u16(20)
            u16(0)
            u16(8)
            u16(0)
            u16(0)
            u32(0)
            u32(compressedData.size)
            u32(uncompressedSizeField)
            u16(nameBytes.size)
            u16(0)
            u16(0)
            u16(0)
            u16(0)
            u32(0)
            u32(offset)
            bytes(nameBytes)
        }.toByteArray()
    }

    private fun endOfCentralDirectory(count: Int, size: Int, offset: Int): ByteArray =
        LittleEndianBuffer().apply {
            u32(0x06054b50)
            u16(0)
            u16(0)
            u16(count)
            u16(count)
            u32(size)
            u32(offset)
            u16(0)
        }.toByteArray()

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
