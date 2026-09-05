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
     * Read inside [renderLock], which is what makes it worth having: the reader
     * asks for several pages as it scrolls and they queue, so a page that was
     * waiting its turn when the reader left the book would otherwise reach a
     * renderer that had been closed while it waited. Leaving the book is the
     * common way to arrive there - the pages ahead are still being drawn.
     *
     * It does not cover a page already inside [PdfRenderer.render] when the
     * book is closed. Closing cannot take the lock, because it happens on the
     * thread that draws and one page can be most of a second; shutting that
     * window means a close that can suspend, which is a change to every
     * platform and to what owns a document.
     */
    @Volatile
    private var closed = false

    /**
     * Measured as the document opens, which is where the other platforms measure
     * theirs.
     *
     * Every one of these is a page opened through [PdfRenderer] - a JNI call
     * each, and hundreds of them on a long book - so where this runs decides
     * whether opening a book stutters. It used to be lazy, on the argument that
     * a document opened for some other reason should not pay for a list nobody
     * asks it for. Nobody was saved: both ways out of here force it, the reader
     * as it lays out its page slots and the text conversion as it starts. What
     * the laziness actually did was move hundreds of JNI calls onto whichever
     * thread asked first - and the reader asks from composition, so a long PDF
     * paid for its own page list on the main thread, on the frames the screen
     * carrying it was still arriving on.
     *
     * Nobody waits longer for it now: opening already runs off the main thread -
     * see `OpenDocumentUseCase`, which is the only place documents are opened.
     */
    override val pageSizes: List<PageSize> = List(renderer.pageCount) { index ->
        renderer.openPage(index).use { page ->
            PageSize(width = page.width.toFloat(), height = page.height.toFloat())
        }
    }

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
     * Lazy is safe here in a way it is not on the platforms whose renderer has
     * an outline of its own: this reads the file through a handle of its own
     * rather than through [renderer], so a chapter list being read while a page
     * is being drawn is two readers of one file rather than two users of one
     * document. That is why this one is not read up front as they are.
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

    // Serialised with drawing for the same reason drawing is serialised with
    // itself: this opens its own handle on the same file, and a page being read
    // while another is being drawn is contention over one document either way.
    // The content keeps a lock of its own as well, for the documents a
    // conversion opens where there is no renderer to contend with.
    //
    // Off the main thread before touching [content], the way drawing already is.
    // The lock only suspends, but the first caller through here also opens the
    // file a second time with a library that can read it, and a PDF being parsed
    // where it was asked for is a PDF being parsed on the thread that draws.
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
