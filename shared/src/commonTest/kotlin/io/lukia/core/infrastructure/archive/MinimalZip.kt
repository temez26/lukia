package io.lukia.core.infrastructure.archive

/**
 * A ZIP archive written by hand, with every entry stored rather than deflated.
 *
 * The reader beside this is common code, and was only ever handed an archive by
 * `jvmTest` through `java.util.zip` - which let the JDK's idea of the format
 * stand in for the format on the two platforms with no JDK. This writes the
 * bytes itself, so one fixture is read on the desktop, on a device and in the
 * simulator.
 *
 * Stored because there is a zlib decompressor in common code and no compressor.
 * `ZipArchiveReaderTest` covers the deflated half on the JVM, where one exists.
 *
 * No CRC is written: the reader does not check one, and a checksum this cannot
 * verify would be a fixture asserting something about itself.
 */
internal fun storedZip(entries: Map<String, ByteArray>): ByteArray {
    val bytes = mutableListOf<Byte>()
    val directory = mutableListOf<Byte>()

    entries.forEach { (name, content) ->
        val localHeaderOffset = bytes.size

        bytes += localHeader(name, content.size)
        bytes += content.toList()

        directory += centralHeader(name, content.size, localHeaderOffset)
    }

    val directoryOffset = bytes.size
    bytes += directory
    bytes += endOfCentralDirectory(entries.size, directory.size, directoryOffset)

    return bytes.toByteArray()
}

private fun localHeader(name: String, size: Int): List<Byte> {
    val nameBytes = name.encodeToByteArray()

    return u32(LocalFileHeaderSignature) +
        u16(ZipVersionNeeded) +
        u16(0) + // flags
        u16(Stored) +
        u16(0) + u16(0) + // modification time and date
        u32(0) + // crc
        u32(size) + // compressed
        u32(size) + // uncompressed
        u16(nameBytes.size) +
        u16(0) + // extra
        nameBytes.toList()
}

private fun centralHeader(name: String, size: Int, localHeaderOffset: Int): List<Byte> {
    val nameBytes = name.encodeToByteArray()

    return u32(CentralDirectorySignature) +
        u16(ZipVersionNeeded) + // version made by
        u16(ZipVersionNeeded) +
        u16(0) + // flags
        u16(Stored) +
        u16(0) + u16(0) + // modification time and date
        u32(0) + // crc
        u32(size) + // compressed
        u32(size) + // uncompressed
        u16(nameBytes.size) +
        u16(0) + // extra
        u16(0) + // comment
        u16(0) + // disk the entry starts on
        u16(0) + // internal attributes
        u32(0) + // external attributes
        u32(localHeaderOffset) +
        nameBytes.toList()
}

private fun endOfCentralDirectory(entries: Int, directorySize: Int, directoryOffset: Int): List<Byte> =
    u32(EndOfCentralDirectorySignature) +
        u16(0) + // this disk
        u16(0) + // the disk the directory starts on
        u16(entries) +
        u16(entries) +
        u32(directorySize) +
        u32(directoryOffset) +
        u16(0) // comment

/** ZIP is little-endian throughout, which is the one thing easy to get backwards. */
private fun u16(value: Int): List<Byte> =
    listOf((value and 0xFF).toByte(), ((value shr 8) and 0xFF).toByte())

private fun u32(value: Int): List<Byte> = List(4) { ((value shr (it * 8)) and 0xFF).toByte() }

private const val LocalFileHeaderSignature = 0x04034b50
private const val CentralDirectorySignature = 0x02014b50
private const val EndOfCentralDirectorySignature = 0x06054b50

/** 2.0, which is what a stored or deflated entry with no extras asks for. */
private const val ZipVersionNeeded = 20

private const val Stored = 0
