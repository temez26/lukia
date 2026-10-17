package io.lukia.features.reader.infrastructure

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.lukia.features.reader.domain.outline.OutlineEntry
import io.lukia.features.reader.domain.page.DocumentPages
import io.lukia.features.reader.domain.page.PageGlyph
import io.lukia.features.reader.domain.page.PageImage
import io.lukia.features.reader.domain.page.PageSize
import io.lukia.features.reader.infrastructure.pdf.PdfOutlineReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.concurrent.Volatile

/**
 * Every handle is opened here rather than inside the document, so that one the
 * next step refuses is given back rather than leaked - see the expect. A
 * document whose construction throws part way is never assigned to anything and
 * so is never closed, and each of these is a file handle the reader does not get
 * back until the process ends.
 */
internal actual fun openPdfPages(path: String): DocumentPages? {
    val file = File(path)

    val descriptor = runCatching {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }.getOrNull() ?: return null

    val renderer = runCatching { PdfRenderer(descriptor) }.getOrElse {
        runCatching { descriptor.close() }
        return null
    }

    return runCatching { AndroidDocumentPages(file, descriptor, renderer) }.getOrElse {
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
        null
    }
}

private class AndroidDocumentPages(
    private val file: File,
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
) : DocumentPages {

    /**
     * [PdfRenderer] allows one open page at a time and throws on the second, so
     * the reader's concurrent page requests have to be serialised rather than
     * merely made safe.
     */
    private val renderLock = Mutex()

    /**
     * Whether the book has been given back.
     *
     * Read inside [renderLock], which is what makes it worth having: pages queue
     * as the reader scrolls, so one waiting its turn when the reader leaves the
     * book would otherwise reach a renderer closed while it waited. Leaving the
     * book with pages ahead still being drawn is the common way there.
     *
     * It does not cover a page already inside [PdfRenderer.render] when the book
     * is closed. Closing cannot take the lock - it happens on the drawing thread
     * and one page can be most of a second - and shutting that window means a
     * close that can suspend, a change to every platform and to what owns a
     * document.
     */
    @Volatile
    private var closed = false

    /**
     * Measured as the document opens, which is where the other platforms measure
     * theirs.
     *
     * Each is a page opened through [PdfRenderer] - one JNI call apiece, and
     * hundreds on a long book - so where this runs decides whether opening a
     * book stutters. It used to be lazy, to spare a document opened for some
     * other reason. That saved nobody: both ways out of here force it anyway
     * (the reader laying out page slots, the text conversion starting), and
     * laziness only moved those JNI calls onto whichever thread asked first -
     * the reader asks from composition, so a long PDF paid for its page list on
     * the main thread while its own screen was still arriving.
     *
     * Nobody waits longer now: opening already runs off the main thread - see
     * `OpenDocumentUseCase`, the only place documents are opened.
     *
     * Measured once per file and read back after that - see [storedPageSizes].
     */
    override val pageSizes: List<PageSize> =
        storedPageSizes(file.path, renderer.pageCount)
            ?: List(renderer.pageCount) { index ->
                renderer.openPage(index).use { page ->
                    PageSize(width = page.width.toFloat(), height = page.height.toFloat())
                }
            }.also { storePageSizes(file.path, it) }

    /** The renderer's own count, so that asking it does not measure every page. */
    override val pageCount: Int = renderer.pageCount

    /**
     * Read out of the file itself, because [PdfRenderer] exposes page count and
     * page bitmaps and nothing else - it has no outline, bookmark or destination
     * API at this app's minimum SDK.
     *
     * Lazy so that opening a book never pays for a chapter list until something
     * asks for one, and so the file is not scanned on the way to the first page.
     *
     * Lazy is safe here in a way it is not on platforms whose renderer has an
     * outline of its own: this reads the file through its own handle rather than
     * through [renderer], so a chapter list read while a page is drawn is two
     * readers of one file rather than two users of one document.
     */
    override val outline: List<OutlineEntry>? by lazy {
        FilePdfByteSource(file).use { PdfOutlineReader(it).read() }
    }

    /**
     * Opened only if somebody asks what is on a page, because it means opening
     * the file a second time with a library that can read one - which
     * [PdfRenderer] cannot, for either the words or the pictures.
     */
    private val content = lazy { AndroidPageContent(file) }

    // Serialised with drawing for the reason drawing is serialised with itself:
    // this opens its own handle on the same file, so a page read while another
    // is drawn is contention over one document. The content keeps its own lock
    // too, for the documents a conversion opens with no renderer to contend
    // with.
    //
    // Off the main thread before touching [content], as drawing already is: the
    // lock only suspends, but the first caller through here also opens the file
    // a second time with a library that can read it, and a PDF parsed where it
    // was asked for is a PDF parsed on the thread that draws.
    override suspend fun pageText(index: Int): List<PageGlyph>? =
        withContext(Dispatchers.IO) {
            renderLock.withLock {
                if (closed) null else content.value.pageText(index)
            }
        }

    override suspend fun pageImages(index: Int): List<PageImage> =
        withContext(Dispatchers.IO) {
            renderLock.withLock {
                if (closed) emptyList() else content.value.pageImages(index)
            }
        }

    override suspend fun renderPage(index: Int, targetWidthPx: Int): ImageBitmap? =
        withContext(Dispatchers.IO) {
            renderLock.withLock {
                if (closed) return@withLock null

                runCatching {
                    renderer.openPage(index).use { page ->
                        val height = targetWidthPx * page.height / page.width

                        val bitmap = Bitmap.createBitmap(
                            targetWidthPx,
                            height,
                            Bitmap.Config.ARGB_8888,
                        )

                        // PdfRenderer draws only what the page paints, so a page
                        // with no background of its own would come out on
                        // transparent black without this.
                        bitmap.eraseColor(PAGE_BACKGROUND)

                        page.render(
                            bitmap,
                            null,
                            null,
                            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY,
                        )

                        bitmap.asImageBitmap()
                    }
                }.getOrNull()
            }
        }

    override fun close() {
        // Marked before anything is closed, so that a page still queued behind
        // the lock finds out rather than starting on a renderer that is going.
        closed = true

        runCatching { renderer.close() }
        runCatching { descriptor.close() }
        // Only if it was ever opened: reading a book cover to cover without
        // translating anything or re-setting it as text never touches this.
        if (content.isInitialized()) runCatching { content.value.close() }
    }

    private companion object {
        const val PAGE_BACKGROUND = 0xFFFFFFFF.toInt()
    }
}
