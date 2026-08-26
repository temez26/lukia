package io.lukia.shared.presentation.components

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runComposeUiTest
import io.lukia.shared.presentation.theme.LukiaTheme
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Surface
import kotlin.test.Test

/**
 * Covers are decoded off the main thread, so what a card shows arrives a beat
 * after it is composed. These pin the three states that decoding can land in -
 * artwork, no artwork, and artwork that would not decode - because the middle
 * one and the last one look identical to the reader but reach the screen by
 * different routes.
 */
@OptIn(ExperimentalTestApi::class)
class BookCoverTest {

    /** A real, decodable image, so the success path is exercised end to end. */
    private val decodablePng: ByteArray =
        Surface.makeRasterN32Premul(4, 6)
            .makeImageSnapshot()
            .encodeToData(EncodedImageFormat.PNG)!!
            .bytes

    /**
     * A book of its own per case, because the decoded artwork is cached for the
     * whole process and filed under the book - so three cases sharing one book
     * would be three readings of whichever ran first.
     */
    private fun ComposeUiTest.setCover(book: String, bytes: ByteArray?) {
        setContent {
            LukiaTheme(darkTheme = false) {
                BookCover(
                    cover = bytes?.let { CoverArtSource(book) { it } },
                    title = "Moby Dick",
                )
            }
        }
    }

    /**
     * Polls rather than waiting for idle: the decode runs on a dispatcher the
     * test clock does not drive, so idleness says nothing about whether it is
     * finished.
     */
    private fun ComposeUiTest.awaitMonogram(present: Boolean) {
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithText("M").fetchSemanticsNodes().isNotEmpty() == present
        }
    }

    @Test
    fun aBookWithNoCoverShowsItsMonogram() = runComposeUiTest {
        setCover(book = "/library/no-cover.epub", bytes = null)

        awaitMonogram(present = true)
    }

    @Test
    fun aBookWithArtworkShowsTheArtworkInsteadOfAMonogram() = runComposeUiTest {
        setCover(book = "/library/with-artwork.epub", bytes = decodablePng)

        awaitMonogram(present = false)
    }

    /**
     * A cover that will not decode has to end where a missing one does. Left
     * unhandled it would sit on the pending tile for ever - a blank card with no
     * way to tell it apart from one still loading.
     */
    @Test
    fun artworkThatWillNotDecodeFallsBackToTheMonogram() = runComposeUiTest {
        setCover(book = "/library/broken-artwork.epub", bytes = byteArrayOf(1, 2, 3, 4))

        awaitMonogram(present = true)
    }
}
