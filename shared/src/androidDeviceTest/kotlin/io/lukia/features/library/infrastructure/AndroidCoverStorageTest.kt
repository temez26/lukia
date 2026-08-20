package io.lukia.features.library.infrastructure

import android.database.sqlite.SQLiteBlobTooBigException
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import io.lukia.core.domain.CoverImage
import io.lukia.core.domain.DocumentInfo
import io.lukia.core.domain.DocumentType
import io.lukia.core.domain.PickedDocument
import io.lukia.core.infrastructure.db.LukiaDatabase
import io.lukia.features.import.infrastructure.cover.MaxStoredCoverBytes
import io.lukia.features.import.infrastructure.cover.MaxStoredCoverEdge
import io.lukia.features.import.infrastructure.cover.shrinkCoverImage
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A shelf's worth of books, which is what an import of several arrives as. */
private const val BooksInALibrary = 8

/** Past the two megabytes a CursorWindow holds, and so past what can be read. */
private const val CoverOverTheWindow = 2_100_000

private const val ArtworkWidth = 2000
private const val ArtworkHeight = 3000

/** An odd multiplier, so that consecutive pixels share no pattern worth compressing. */
private const val NoiseStride = 1103515245

private const val LosslessQuality = 100

private const val BookSizeBytes = 1024L

/**
 * The limit on a stored cover, against the platform that imposes it.
 *
 * Android hands a query's results over through a CursorWindow of two megabytes,
 * and a row too large for one does not come back short - the read throws. That
 * read is the whole library, so one book's cover art could empty the shelf and
 * leave a retry button in its place, which is what importing a batch of books
 * with large covers used to do.
 */
class AndroidCoverStorageTest {

    private val database = LukiaDatabase(
        AndroidSqliteDriver(
            schema = LukiaDatabase.Schema,
            context = InstrumentationRegistry.getInstrumentation().targetContext,
            // In memory: a test has no business in the app's own library.
            name = null,
        ),
    )

    private val repository = SqlLibraryRepository(database, UnconfinedTestDispatcher())

    @Test
    fun aLibraryOfCoversAtTheStoredLimitComesBackWhole() = runTest {
        repeat(BooksInALibrary) { index ->
            repository.addDocument(book(index, coverBytes = MaxStoredCoverBytes))
        }

        assertEquals(BooksInALibrary, repository.getDocuments().size)
    }

    /**
     * The reason the limit exists, kept as a test so that it is a fact about
     * this platform rather than a claim in a comment. If Android ever reads a
     * row this size, this is what will say so.
     */
    @Test
    fun aCoverOverTheWindowTakesTheWholeLibraryWithIt() = runTest {
        repository.addDocument(book(0, coverBytes = CoverOverTheWindow))

        assertFailsWith<SQLiteBlobTooBigException> { repository.getDocuments() }
    }

    @Test
    fun artworkIsShrunkToSomethingTheLibraryCanCarry() {
        val shrunk = shrinkCoverImage(largeArtwork(), MaxStoredCoverEdge)

        assertNotNull(shrunk)
        assertEquals(MaxStoredCoverEdge, longestEdgeOf(shrunk))
        assertTrue(
            shrunk.size <= MaxStoredCoverBytes,
            "A shrunk cover was ${shrunk.size} bytes, over the $MaxStoredCoverBytes stored.",
        )
    }

    /** A publisher's artwork, at the size publishers actually ship it. */
    private fun largeArtwork(): ByteArray {
        val bitmap = Bitmap.createBitmap(ArtworkWidth, ArtworkHeight, Bitmap.Config.ARGB_8888)

        // Noise rather than a flat colour: blank artwork compresses to nothing,
        // which would prove nothing about what shrinking is for.
        val pixels = IntArray(bitmap.width * bitmap.height) { it * NoiseStride }
        bitmap.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        return ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, LosslessQuality, output)
            output.toByteArray()
        }
    }

    private fun longestEdgeOf(imageBytes: ByteArray): Int {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, bounds)

        return maxOf(bounds.outWidth, bounds.outHeight)
    }

    private fun book(index: Int, coverBytes: Int) = DocumentInfo(
        document = PickedDocument(
            fileName = "book-$index.pdf",
            extension = "pdf",
            location = "content://books/$index",
        ),
        type = DocumentType.PDF,
        exists = true,
        sizeBytes = BookSizeBytes,
        coverImage = CoverImage(ByteArray(coverBytes)),
    )
}
