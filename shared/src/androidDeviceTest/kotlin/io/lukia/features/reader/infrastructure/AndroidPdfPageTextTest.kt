package io.lukia.features.reader.infrastructure

import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import io.lukia.core.infrastructure.pdf.MinimalPdf
import io.lukia.features.reader.domain.page.pageSentences
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Reading the words off a page, on Android.
 *
 * The desktop pins this in `PdfPageTextTest` and iOS in `PdfKitPageTextTest`;
 * this platform had neither, and it is the one with the most devices on it and
 * the most machinery in the way. `PdfRenderer` offers no text below API 35, so
 * Android reads through pdfbox-android instead - a whole second implementation,
 * with its own glyph stripping and its own lock around one shared document.
 *
 * Runs against the same hand-written PDF the other two read, so a disagreement
 * between the three points at the platform rather than at the file.
 */
class AndroidPdfPageTextTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val file = File(context.cacheDir, "page-text-test.pdf").apply {
        writeBytes(MinimalPdf.withText("Near the top." to 740, "Near the bottom." to 60))
    }

    /**
     * What `LukiaApplication` does at startup, done again here.
     *
     * pdfbox-android reads the base-14 font metrics out of its own assets, and
     * without a `Context` to find them it answers every page with no text at
     * all rather than with an error. The app initialises it in its `Application`
     * and this process is not the app's - so a test that skipped this would be
     * asserting about a reader that was never switched on.
     */
    @BeforeTest
    fun initialisePdfBox() {
        PDFBoxResourceLoader.init(context)
    }

    @AfterTest
    fun tearDown() {
        file.delete()
    }

    @Test
    fun readsTheWordsOffAPage() = runTest {
        openTestPdf(file.absolutePath).use { pages ->
            val sentences = pageSentences(assertNotNull(pages.pageText(0), "Android read no text layer"))

            assertEquals(
                listOf("Near the top.", "Near the bottom."),
                sentences.map { it.text },
            )
        }
    }

    /**
     * PDF measures upwards from the bottom of the page and every screen measures
     * downwards. A quad that came back unconverted would put a translation at
     * the far end of the page from the words it belongs to - which is invisible
     * in a list of sentences and obvious on a page.
     */
    @Test
    fun quadsAreMeasuredDownwardsFromTheTopOfThePage() = runTest {
        openTestPdf(file.absolutePath).use { pages ->
            val sentences = pageSentences(assertNotNull(pages.pageText(0)))
            val top = sentences.first { it.text.contains("top") }
            val bottom = sentences.first { it.text.contains("bottom") }

            assertTrue(top.bounds.top < bottom.bounds.top, "$top came out below $bottom")
            assertTrue(top.bounds.top < 120f, "the top line was not near the top: ${top.bounds}")
        }
    }

    /**
     * Two readers at once, which is what re-setting a book as text actually
     * does - a handle per core, all of them asking at the same time.
     *
     * pdfbox-android is one document behind a lock here, and a lock that stopped
     * holding would not fail cleanly: it would hand back a page of somebody
     * else's glyphs, or take the process down from inside the library.
     */
    @Test
    fun twoReadersAtOnceEachGetTheWholePage() = runTest {
        openTestPdf(file.absolutePath).use { pages ->
            val both = coroutineScope {
                List(2) { async { pageSentences(assertNotNull(pages.pageText(0))).map { it.text } } }
                    .map { it.await() }
            }

            assertEquals(both.first(), both.last(), "two readers disagreed about one page")
            assertEquals(listOf("Near the top.", "Near the bottom."), both.first())
        }
    }
}
