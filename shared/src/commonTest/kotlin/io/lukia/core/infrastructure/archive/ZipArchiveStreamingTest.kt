package io.lukia.core.infrastructure.archive

import io.lukia.core.infrastructure.ByteArraySource
import io.lukia.core.infrastructure.ByteSource
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

/**
 * That reading one entry out of an archive does not read the archive.
 *
 * The reason this reader takes a [ByteSource] at all, and the one thing the
 * tests around it cannot show: every other test passes a [ByteArray], which
 * reads exactly the same whether the reader is careful or slurps the lot. So
 * this one counts the bytes.
 *
 * It guards a real regression rather than a hypothetical one. The import used to
 * load the whole document to find its cover and its title, and the books people
 * import are scanned PDFs and illustrated EPUBs of a hundred megabytes and more -
 * which is a size Android's heap does not have.
 */
class ZipArchiveStreamingTest {

    @Test
    fun readingOneEntryReadsAlmostNoneOfTheArchive() {
        val wanted = "the cover".encodeToByteArray()
        val ballast = ByteArray(BALLAST_SIZE) { (it * 31).toByte() }

        val archive = CountingSource(
            storedZip(
                mapOf(
                    "cover.txt" to wanted,
                    // Stored rather than deflated, so its size in the file is its
                    // size here - a compressed one would prove nothing about how
                    // much of the file was touched.
                    "big.bin" to ballast,
                ),
            ),
        )

        val read = ZipArchiveReader(archive).readEntry("cover.txt")

        assertContentEquals(wanted, read)
        assertTrue(
            archive.bytesRead < BALLAST_SIZE / 2,
            "reading one small entry read ${archive.bytesRead} bytes of a ${archive.size} byte archive",
        )
    }

    @Test
    fun listingWhatIsInAnArchiveReadsNoneOfIt() {
        val ballast = ByteArray(BALLAST_SIZE) { (it * 17).toByte() }
        val archive = CountingSource(storedZip(mapOf("big.bin" to ballast)))

        assertContentEquals(setOf("big.bin").toList(), ZipArchiveReader(archive).entryNames().toList())
        assertTrue(
            archive.bytesRead < BALLAST_SIZE / 2,
            "listing an archive read ${archive.bytesRead} of its ${archive.size} bytes",
        )
    }

    /** A source that remembers how much of itself was asked for. */
    private class CountingSource(bytes: ByteArray) : ByteSource {

        private val delegate = ByteArraySource(bytes)

        var bytesRead = 0L
            private set

        override val size: Long = delegate.size

        override fun read(offset: Long, length: Int): ByteArray =
            delegate.read(offset, length).also { bytesRead += it.size }
    }

    private companion object {
        /**
         * Large enough that reading it would be unmistakable, and small enough
         * that the test stays quick. The tail scanned for the end-of-directory
         * record is 64KB on its own, so the ballast has to be well past that.
         */
        const val BALLAST_SIZE = 4 * 1024 * 1024
    }
}
