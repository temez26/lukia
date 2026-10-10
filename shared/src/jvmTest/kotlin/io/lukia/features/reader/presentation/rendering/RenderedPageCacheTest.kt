package io.lukia.features.reader.presentation.rendering

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame

/**
 * Drawing a page is the most expensive thing the reader does, so what is worth
 * pinning here is when it does not happen: a page already drawn, and a page two
 * callers want at once.
 */
class RenderedPageCacheTest {

    private fun page() = ImageBitmap(384, 512)

    /**
     * The reader asks for every page twice as a matter of course - each page
     * draws the one after it so that arriving there is not a wait, and that page
     * then asks for itself as it is composed. Both miss the cache, because
     * neither has finished.
     */
    @Test
    fun twoCallersWantingOnePageDrawItOnceBetweenThem() = runTest {
        val cache = RenderedPageCache()
        val drawing = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var draws = 0

        val ahead = async {
            cache.page(index = 3, widthPx = 384) {
                draws++
                drawing.complete(Unit)
                release.await()
                page()
            }
        }

        // The first caller is inside the render and has not stored anything yet,
        // which is exactly the window the second one used to fall into.
        drawing.await()

        val itself = async {
            cache.page(index = 3, widthPx = 384) {
                draws++
                page()
            }
        }

        runCurrent()
        release.complete(Unit)

        assertSame(ahead.await(), itself.await())
        assertEquals(1, draws)
    }

    /**
     * A page scrolled past takes its coroutine with it. Whoever was waiting
     * behind it draws the page themselves rather than being left holding
     * nothing.
     */
    @Test
    fun aPageWhoseFirstCallerGaveUpIsStillDrawnForTheNext() = runTest {
        val cache = RenderedPageCache()
        val drawing = CompletableDeferred<Unit>()
        var draws = 0

        val abandoned = async {
            cache.page(index = 3, widthPx = 384) {
                draws++
                drawing.complete(Unit)
                CompletableDeferred<Unit>().await()
                page()
            }
        }

        drawing.await()

        val waiting = async {
            cache.page(index = 3, widthPx = 384) {
                draws++
                page()
            }
        }

        runCurrent()
        abandoned.cancel()

        assertNotNull(waiting.await())
        assertEquals(2, draws)
    }

    /** Scrolling back over a spread must not draw it again. */
    @Test
    fun aPageAlreadyDrawnIsHandedBackAsItWas() = runTest {
        val cache = RenderedPageCache()
        var draws = 0

        val first = cache.page(index = 3, widthPx = 384) {
            draws++
            page()
        }

        val second = cache.page(index = 3, widthPx = 384) {
            draws++
            page()
        }

        assertSame(first, second)
        assertEquals(1, draws)
    }

    /**
     * Every entry is drawn at one width, so a window that changes width makes
     * the whole cache stale at once.
     */
    @Test
    fun aChangeOfWidthDropsWhatWasDrawnForTheOldOne() = runTest {
        val cache = RenderedPageCache()
        var draws = 0

        cache.page(index = 3, widthPx = 384) {
            draws++
            page()
        }

        cache.page(index = 3, widthPx = 512) {
            draws++
            ImageBitmap(512, 683)
        }

        cache.page(index = 3, widthPx = 384) {
            draws++
            page()
        }

        assertEquals(3, draws)
    }
}
