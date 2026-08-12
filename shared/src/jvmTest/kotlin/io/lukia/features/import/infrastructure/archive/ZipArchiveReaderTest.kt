package io.lukia.features.import.infrastructure.archive

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ZipArchiveReaderTest {

    @Test
    fun readsStoredEntry() {
        val content = "hello world".encodeToByteArray()
        val zipBytes = buildZip { zip ->
            val entry = ZipEntry("stored.txt")
            entry.method = ZipEntry.STORED
            entry.size = content.size.toLong()
            entry.crc = java.util.zip.CRC32().apply { update(content) }.value
            zip.putNextEntry(entry)
            zip.write(content)
            zip.closeEntry()
        }

        val reader = ZipArchiveReader(zipBytes)

        assertEquals(content.toList(), reader.readEntry("stored.txt")?.toList())
    }

    @Test
    fun readsDeflatedEntry() {
        val content = "hello world, deflated this time around".encodeToByteArray()
        val zipBytes = buildZip { zip ->
            zip.putNextEntry(ZipEntry("deflated.txt"))
            zip.write(content)
            zip.closeEntry()
        }

        val reader = ZipArchiveReader(zipBytes)

        assertEquals(content.toList(), reader.readEntry("deflated.txt")?.toList())
    }

    @Test
    fun listsAllEntryNames() {
        val zipBytes = buildZip { zip ->
            zip.putNextEntry(ZipEntry("a.txt"))
            zip.write("a".encodeToByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("b.txt"))
            zip.write("b".encodeToByteArray())
            zip.closeEntry()
        }

        val reader = ZipArchiveReader(zipBytes)

        assertEquals(setOf("a.txt", "b.txt"), reader.entryNames())
    }

    @Test
    fun missingEntryReturnsNull() {
        val zipBytes = buildZip { zip ->
            zip.putNextEntry(ZipEntry("a.txt"))
            zip.write("a".encodeToByteArray())
            zip.closeEntry()
        }

        val reader = ZipArchiveReader(zipBytes)

        assertNull(reader.readEntry("missing.txt"))
    }

    @Test
    fun malformedArchiveReturnsNoEntries() {
        val reader = ZipArchiveReader("not a zip file".encodeToByteArray())

        assertTrue(reader.entryNames().isEmpty())
    }

    private fun buildZip(block: (ZipOutputStream) -> Unit): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use(block)
        return output.toByteArray()
    }
}
