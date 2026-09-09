package io.lukia.features.library.infrastructure

import android.database.sqlite.SQLiteBlobTooBigException
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import io.lukia.core.domain.document.CoverImage
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.domain.document.DocumentType
import io.lukia.core.domain.document.PickedDocument
import io.lukia.core.infrastructure.db.LukiaDatabase
import io.lukia.core.infrastructure.shrinkCoverImage
import io.lukia.features.library.infrastructure.importing.cover.MaxStoredCoverBytes
import io.lukia.features.library.infrastructure.importing.cover.MaxStoredCoverEdge
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A shelf's worth of books, which is what an import of several arrives as. */
private const val BooksInALibrary = 8

/** Past the two megabytes a CursorWindow is configured to hold. */
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
 * Android hands a query's results over through a CursorWindow, and on the
 * versions Lukia opens on - it goes back to API 23 - that window is the two
 * megabytes `config_cursorWindowSize` still says it is. A row too large for one
 * does not come back short there: the read throws. That read is the whole
 * library, so one book's cover art could empty the shelf and leave a retry
 * button in its place, which is what importing a batch of books with large
 * covers used to do.
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
     * The reason the limit exists, kept as a test so it is a fact about the
     * platform rather than a claim in a comment - and written to hold on every
     * version, because they no longer agree.
     *
     * Up to Android 14 a cover this size throws on the way back and takes the
     * shelf with it. Android 16 grows the window to the row instead and returns
     * a twenty-five megabyte blob without complaint, while still reporting the
     * same two-megabyte window size. Asserting the throw alone would fail on a
     * new device and pass on an old one.
     *
     * Pinned instead is what holds on both: the shelf arrives whole or fails
     * recognisably. It never comes back short, and the failure is never anything
     * but the row being too large - either would be a library losing books.
     */
    @Test
    fun aCoverOverTheWindowNeverLeavesTheLibraryShort() = runTest {
        repository.addDocument(book(0, coverBytes = CoverOverTheWindow))

        val shelf = runCatching { repository.getDocuments() }

        shelf.fold(
            onSuccess = { assertEquals(1, it.size) },
            onFailure = { assertIs<SQLiteBlobTooBigException>(it) },
        )
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
