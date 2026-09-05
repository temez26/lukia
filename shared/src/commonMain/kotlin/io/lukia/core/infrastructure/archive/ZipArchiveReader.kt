package io.lukia.core.infrastructure.archive

/**
 * Minimal read-only ZIP reader operating on an in-memory archive.
 * Supports only the subset of the ZIP format (no Zip64) needed to pull named
 * entries out of an EPUB or a DOCX, both of which are small enough to load
 * fully into memory.
 */
internal class ZipArchiveReader(private val data: ByteArray) {

    private val entriesByName: Map<String, ZipEntryInfo> by lazy { readCentralDirectory() }

    /**
     * Whether the archive as a whole claims to expand to more than
     * [MAX_ARCHIVE_SIZE], in which case nothing is read out of it at all.
     *
     * Asked of the directory once rather than counted down as entries are read.
     * A running total would be shared mutable state, and the caller that most
     * needs this cap is the one that reads a book's chapters concurrently -
     * every one of them would race the others to spend it. The directory
     * already states what each entry decodes to, so the whole question can be
     * answered before a single byte is inflated.
     */
    private val isBomb: Boolean by lazy {
        entriesByName.values.sumOf { it.uncompressedSize.coerceAtLeast(0).toLong() } >
            MAX_ARCHIVE_SIZE
    }

    fun entryNames(): Set<String> = entriesByName.keys

    fun readEntry(name: String): ByteArray? {
        if (isBomb) return null

        val entry = entriesByName[name] ?: return null
        return readEntryData(entry)
    }

    private fun readEntryData(entry: ZipEntryInfo): ByteArray? {
        if (entry.compressedSize < 0 || entry.compressedSize > MAX_ENTRY_SIZE) return null
        if (entry.uncompressedSize < 0 || entry.uncompressedSize > MAX_ENTRY_SIZE) return null

        val localHeaderStart = entry.localHeaderOffset
        if (localHeaderStart < 0 || localHeaderStart + LOCAL_HEADER_SIZE > data.size) return null
        if (readU32(localHeaderStart) != LOCAL_FILE_HEADER_SIGNATURE) return null

        val nameLength = readU16(localHeaderStart + 26)
        val extraLength = readU16(localHeaderStart + 28)
        val dataStart = localHeaderStart + LOCAL_HEADER_SIZE + nameLength + extraLength

        if (dataStart < 0 || dataStart + entry.compressedSize > data.size) return null

        val compressed = data.copyOfRange(dataStart, dataStart + entry.compressedSize)

        return when (entry.compressionMethod) {
            0 -> compressed
            8 -> runCatching { inflateRaw(compressed, entry.uncompressedSize) }.getOrNull()
            else -> null
        }
    }

    private fun readCentralDirectory(): Map<String, ZipEntryInfo> {
        if (data.size < EOCD_MIN_SIZE) return emptyMap()

        val eocdOffset = findEndOfCentralDirectory() ?: return emptyMap()
        val centralDirOffset = readU32(eocdOffset + 16)

        if (centralDirOffset < 0 || centralDirOffset >= data.size) return emptyMap()

        val entries = LinkedHashMap<String, ZipEntryInfo>()
        var offset = centralDirOffset

        while (offset + CENTRAL_HEADER_SIZE <= data.size && readU32(offset) == CENTRAL_DIR_SIGNATURE) {
            val compressionMethod = readU16(offset + 10)
            val compressedSize = readU32(offset + 20)
            val uncompressedSize = readU32(offset + 24)
            val nameLength = readU16(offset + 28)
            val extraLength = readU16(offset + 30)
            val commentLength = readU16(offset + 32)
            val localHeaderOffset = readU32(offset + 42)

            val nameStart = offset + CENTRAL_HEADER_SIZE
            if (nameStart + nameLength > data.size) break

            val name = data.decodeToString(nameStart, nameStart + nameLength)

            entries[name] = ZipEntryInfo(
                compressionMethod = compressionMethod,
                compressedSize = compressedSize,
                uncompressedSize = uncompressedSize,
                localHeaderOffset = localHeaderOffset,
            )

            offset = nameStart + nameLength + extraLength + commentLength
        }

        return entries
    }

    private fun findEndOfCentralDirectory(): Int? {
        val minOffset = maxOf(0, data.size - EOCD_SEARCH_WINDOW)
        var offset = data.size - EOCD_MIN_SIZE

        while (offset >= minOffset) {
            if (readU32(offset) == EOCD_SIGNATURE) return offset
            offset--
        }
        return null
    }

    private fun readU16(offset: Int): Int = (data[offset].toInt() and 0xFF) or
        ((data[offset + 1].toInt() and 0xFF) shl 8)

    private fun readU32(offset: Int): Int = (data[offset].toInt() and 0xFF) or
        ((data[offset + 1].toInt() and 0xFF) shl 8) or
        ((data[offset + 2].toInt() and 0xFF) shl 16) or
        ((data[offset + 3].toInt() and 0xFF) shl 24)

    private class ZipEntryInfo(
        val compressionMethod: Int,
        val compressedSize: Int,
        val uncompressedSize: Int,
        val localHeaderOffset: Int,
    )

    private companion object {
        const val EOCD_SIGNATURE = 0x06054b50
        const val CENTRAL_DIR_SIGNATURE = 0x02014b50
        const val LOCAL_FILE_HEADER_SIGNATURE = 0x04034b50
        const val EOCD_MIN_SIZE = 22
        const val CENTRAL_HEADER_SIZE = 46
        const val LOCAL_HEADER_SIZE = 30
        const val EOCD_SEARCH_WINDOW = EOCD_MIN_SIZE + 65535

        // Reject declared sizes above this before allocating or inflating, so a crafted
        // archive can't force an oversized allocation via a lying size field. Generous
        // rather than tight because the largest thing read through here is a DOCX's
        // `word/document.xml`, which for a book-length document is the whole book as
        // XML in one entry - a limit sized for EPUB chapters would reject real files.
        const val MAX_ENTRY_SIZE = 64 * 1024 * 1024

        // And what everything in the archive may decompress to together, which
        // is the limit that actually holds. The per-entry one above is no limit
        // at all on its own: a book is read a chapter at a time and `readEpub`
        // reads every chapter at once, so an EPUB declaring two hundred entries
        // of the maximum size would have each of them pass the check
        // individually and all of them be in memory together. Six hundred
        // megabytes is far past the largest real book and far short of what a
        // phone dies of.
        const val MAX_ARCHIVE_SIZE = 600L * 1024 * 1024
    }
}
