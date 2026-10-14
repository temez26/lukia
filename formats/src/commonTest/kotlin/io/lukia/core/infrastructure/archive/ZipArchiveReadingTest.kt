package io.lukia.core.infrastructure.archive

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The archive reader, on every platform rather than only on the one with a
 * `java.util.zip` to write the fixture with.
 *
 * `ZipArchiveReaderTest` in `jvmTest` covers the deflated entries and size caps,
 * which need a compressor. Here is what must be true wherever the app runs: the
 * central directory is found and read, an entry's data begins where its *local*
 * header says rather than where the directory's copy does, and a damaged archive
 * gives up nothing rather than raising.
 */
class ZipArchiveReadingTest {

    private val entries = mapOf(
        "mimetype" to "application/epub+zip".encodeToByteArray(),
        "META-INF/container.xml" to "<container/>".encodeToByteArray(),
        "OEBPS/text/one.xhtml" to "<html>Call me Ishmael.</html>".encodeToByteArray(),
    )

    @Test
    fun readsAStoredEntryBackWhole() {
        val reader = ZipArchiveReader(storedZip(entries))

        assertContentEquals(entries.getValue("OEBPS/text/one.xhtml"), reader.readEntry("OEBPS/text/one.xhtml"))
    }

    @Test
    fun listsEveryEntryTheDirectoryNames() {
        assertEquals(entries.keys, ZipArchiveReader(storedZip(entries)).entryNames())
    }

    /** A name that is not in the archive is nothing, not a guess at a near one. */
    @Test
    fun anEntryThatIsNotThereIsNothing() {
        assertNull(ZipArchiveReader(storedZip(entries)).readEntry("OEBPS/text/two.xhtml"))
    }

    /** Names are matched whole, including the directories in front of them. */
    @Test
    fun anEntryIsFoundByItsWholePath() {
        val reader = ZipArchiveReader(storedZip(entries))

        assertNull(reader.readEntry("one.xhtml"))
        assertNull(reader.readEntry("/OEBPS/text/one.xhtml"))
    }

    @Test
    fun anEmptyEntryIsAnEntryWithNothingInIt() {
        val reader = ZipArchiveReader(storedZip(mapOf("empty.txt" to ByteArray(0))))

        assertContentEquals(ByteArray(0), reader.readEntry("empty.txt"))
    }

    @Test
    fun anArchiveWithNoEntriesHoldsNothing() {
        assertTrue(ZipArchiveReader(storedZip(emptyMap())).entryNames().isEmpty())
    }

    @Test
    fun somethingThatIsNotAnArchiveHoldsNothing() {
        val reader = ZipArchiveReader("not an archive, just some words".encodeToByteArray())

        assertTrue(reader.entryNames().isEmpty())
        assertNull(reader.readEntry("anything"))
    }

    /**
     * A file cut short loses its directory, which sits at the end - so this is
     * what a half-downloaded book looks like, and it has to come back empty
     * rather than reading whatever the truncation left behind.
     */
    @Test
    fun anArchiveCutShortHoldsNothing() {
        val whole = storedZip(entries)
        val cut = whole.copyOfRange(0, whole.size - 8)

        assertTrue(ZipArchiveReader(cut).entryNames().isEmpty())
    }

    @Test
    fun aFileWithNothingInItHoldsNothing() {
        assertTrue(ZipArchiveReader(ByteArray(0)).entryNames().isEmpty())
    }
}
