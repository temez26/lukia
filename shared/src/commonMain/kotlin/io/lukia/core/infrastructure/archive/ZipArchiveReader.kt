package io.lukia.core.infrastructure.archive

import io.lukia.core.infrastructure.ByteArraySource
import io.lukia.core.infrastructure.ByteSource

/**
 * Minimal read-only ZIP reader over a [ByteSource].
 *
 * Supports only the subset of the ZIP format (no Zip64) needed to pull named
 * entries out of an EPUB or a DOCX.
 *
 * Random access rather than a loaded archive, because a ZIP is built to be read
 * that way: the list of what is in it sits at the end, each entry says where its
 * own bytes start, and nothing in between has to be looked at. Reading it that
 * way is the difference between an import that costs the size of one cover and
 * one that costs the size of the book - and the books people import are hundreds
 * of megabytes.
 *
 * What is held is the central directory and whichever entry is being read. The
 * directory is a few tens of kilobytes for a book of five hundred chapters,
 * which is worth keeping: it is read once and asked about repeatedly.
 */
internal class ZipArchiveReader(private val source: ByteSource) {

    /** For an archive already in memory, and for tests. */
    constructor(data: ByteArray) : this(ByteArraySource(data))

    private val entriesByName: Map<String, ZipEntryInfo> by lazy { readCentralDirectory() }

    /**
     * Whether the archive as a whole claims to expand to more than
     * [MAX_ARCHIVE_SIZE], in which case nothing is read out of it at all.
     *
     * Asked of the directory once rather than counted down as entries are read.
     * A running total would be shared mutable state, and the caller that most
     * needs this cap reads a book's chapters concurrently - every one racing the
     * others to spend it. The directory already states what each entry decodes
     * to, so the question is answered before a byte is inflated.
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

        val dataStart = entryDataStart(entry) ?: return null
        val compressed = source.read(dataStart, entry.compressedSize)

        // Short of what the directory promised, which means the archive is
        // truncated. Inflating what did arrive would answer with half a chapter
        // rather than with nothing.
        if (compressed.size < entry.compressedSize) return null

        return when (entry.compressionMethod) {
            STORED -> compressed
            DEFLATED -> runCatching { inflateRaw(compressed, entry.uncompressedSize) }.getOrNull()
            else -> null
        }
    }

    /**
     * Where an entry's bytes begin, past a local header whose name and extra
     * fields are as long as that header says.
     *
     * Read from the local header rather than from the central directory's copy of
     * the same two lengths: the two are allowed to differ, and it is the local one
     * that says where the data actually starts.
     */
    private fun entryDataStart(entry: ZipEntryInfo): Long? {
        val header = entry.localHeaderOffset.toLong()

        if (header < 0 || header + LOCAL_HEADER_SIZE > source.size) return null

        val local = source.read(header, LOCAL_HEADER_SIZE)

        // Short of a whole header, or not a header at all: either way there is
        // no entry here, whatever the directory said.
        if (local.size < LOCAL_HEADER_SIZE || local.u32(0) != LOCAL_FILE_HEADER_SIGNATURE) return null

        val nameLength = local.u16(26)
        val extraLength = local.u16(28)
        val dataStart = header + LOCAL_HEADER_SIZE + nameLength + extraLength

        return dataStart.takeIf { it >= 0 && it + entry.compressedSize <= source.size }
    }

    /**
     * The archive's own list of what is in it, read out of the end of the file.
     *
     * The whole directory is held rather than walked twice, because that is what
     * is asked repeatedly - by name, for a size cap, and for the entry list an
     * EPUB's manifest is checked against.
     */
    private fun readCentralDirectory(): Map<String, ZipEntryInfo> {
        if (source.size < EOCD_MIN_SIZE) return emptyMap()

        val end = endOfCentralDirectory() ?: return emptyMap()
        val directoryOffset = end.window.u32(end.at + 16)
        val directorySize = end.window.u32(end.at + 12)

        if (directoryOffset < 0 || directoryOffset >= source.size) return emptyMap()
        if (directorySize < 0) return emptyMap()

        val directory = source.read(directoryOffset.toLong(), directorySize)

        val entries = LinkedHashMap<String, ZipEntryInfo>()
        var offset = 0

        while (
            offset + CENTRAL_HEADER_SIZE <= directory.size &&
            directory.u32(offset) == CENTRAL_DIR_SIGNATURE
        ) {
            val compressionMethod = directory.u16(offset + 10)
            val compressedSize = directory.u32(offset + 20)
            val uncompressedSize = directory.u32(offset + 24)
            val nameLength = directory.u16(offset + 28)
            val extraLength = directory.u16(offset + 30)
            val commentLength = directory.u16(offset + 32)
            val localHeaderOffset = directory.u32(offset + 42)

            val nameStart = offset + CENTRAL_HEADER_SIZE
            if (nameStart + nameLength > directory.size) break

            val name = directory.decodeToString(nameStart, nameStart + nameLength)

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

    /**
     * The end-of-central-directory record, and the tail of the file it was found
     * in.
     *
     * A ZIP has no header saying where its directory is; the record that does sit
     * at the very end, behind a comment of up to 65535 bytes. So the tail is read
     * once and scanned backwards for the signature, rather than the file being
     * read byte by byte from the other end.
     */
    private fun endOfCentralDirectory(): EndOfDirectory? {
        val windowSize = minOf(source.size, EOCD_SEARCH_WINDOW.toLong()).toInt()
        val windowStart = source.size - windowSize
        val window = source.read(windowStart, windowSize)

        var at = window.size - EOCD_MIN_SIZE

        while (at >= 0) {
            if (window.u32(at) == EOCD_SIGNATURE) return EndOfDirectory(window, at)
            at--
        }

        return null
    }

    /** Where the archive says its directory is, and the bytes that said so. */
    private class EndOfDirectory(val window: ByteArray, val at: Int)

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

        /** The two compression methods a book is ever stored with. */
        const val STORED = 0
        const val DEFLATED = 8

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

private fun ByteArray.u16(at: Int): Int = (this[at].toInt() and 0xFF) or
    ((this[at + 1].toInt() and 0xFF) shl 8)

private fun ByteArray.u32(at: Int): Int = (this[at].toInt() and 0xFF) or
    ((this[at + 1].toInt() and 0xFF) shl 8) or
    ((this[at + 2].toInt() and 0xFF) shl 16) or
    ((this[at + 3].toInt() and 0xFF) shl 24)
