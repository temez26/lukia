package io.lukia.features.reader.presentation

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.lukia.core.domain.runCatchingApp
import io.lukia.core.domain.toAppError
import io.lukia.core.presentation.UiState
import io.lukia.core.presentation.dataOrNull
import io.lukia.features.reader.application.OpenDocumentUseCase
import io.lukia.features.reader.domain.ReaderDocument
import io.lukia.features.reader.domain.ReaderOutline
import io.lukia.features.reader.domain.ReaderSettings
import io.lukia.features.reader.domain.ReadingProgress
import io.lukia.features.reader.domain.ReadingProgressRepository
import io.lukia.features.reader.domain.asTableOfContents
import io.lukia.features.reader.infrastructure.ReaderSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * How much drawn page is kept in memory, counted in pixels rather than pages.
 *
 * A page is four bytes per pixel, and a page's size varies enormously - a phone
 * page is a few megabytes where a zoomed desktop page is twenty. Counting pages
 * would mean choosing a number safe for the largest, which then holds far too
 * little on the smallest. This budget adapts: roughly a dozen pages on a phone,
 * three on a zoomed-in desktop, and about 64 MB either way.
 */
private const val MAX_CACHED_PIXELS = 16_000_000

/** Kept regardless of the budget, so the page being read is never evicted. */
private const val MIN_CACHED_PAGES = 3

class ReaderViewModel(
    private val location: String,
    private val openDocumentUseCase: OpenDocumentUseCase,
    private val settingsRepository: ReaderSettingsRepository,
    private val progressRepository: ReadingProgressRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<ReaderDocument>>(UiState.Loading)
    val uiState: StateFlow<UiState<ReaderDocument>> = _uiState.asStateFlow()

    /**
     * The page to open at, once it is known - null while the book is still
     * being opened and the stored position still being read.
     *
     * Settled once per opening and never changed afterwards, so the screen can
     * treat it as an instruction to carry out rather than a position to keep
     * following. A value that tracked the reader would send them back to where
     * they started every time it arrived.
     */
    private val _initialPage = MutableStateFlow<Int?>(null)
    val initialPage: StateFlow<Int?> = _initialPage.asStateFlow()

    /**
     * Exposed straight from the repository rather than copied into this
     * ViewModel: the settings outlive the open book, and mirroring them here
     * would be one more thing that could disagree with what was stored.
     */
    val settings: StateFlow<ReaderSettings> = settingsRepository.settings

    private val _outline = MutableStateFlow<ReaderOutline>(ReaderOutline.Loading)
    val outline: StateFlow<ReaderOutline> = _outline.asStateFlow()

    fun updateSettings(settings: ReaderSettings) {
        settingsRepository.update(settings)
    }

    /**
     * Least-recently-used, keyed by page index. Every entry is drawn at
     * [cachedWidthPx]; when the window changes width the whole cache is stale at
     * once, so it is dropped rather than tracked per page.
     */
    private val renderedPages = LinkedHashMap<Int, ImageBitmap>()
    private val cacheLock = Mutex()
    private var cachedWidthPx = 0

    init {
        open()
    }

    private fun open() {
        viewModelScope.launch {
            // getOrThrow re-raises the use case's own failure so that it and an
            // unanticipated throw are both narrowed in the same place.
            val state = runCatchingApp { openDocumentUseCase(location).getOrThrow() }
                .fold(
                    onSuccess = { UiState.Success(it) },
                    onFailure = { UiState.Failure(it.toAppError()) }
                )

            _uiState.value = state

            if (state is UiState.Success) {
                restorePosition(state.data)
                loadOutline(state.data)
            }
        }
    }

    /**
     * Where this book was left off, or its first page if it has never been
     * opened - which is the same instruction, so an unread book is not a case
     * of its own.
     *
     * Clamped to the book: a document can be replaced by a shorter one under
     * the same name, and a stored page past the end would otherwise open at
     * nothing.
     */
    private suspend fun restorePosition(document: ReaderDocument) {
        val lastPage = document.pages.pageCount - 1
        val stored = runCatchingApp { progressRepository.progressFor(location) }.getOrNull()

        _initialPage.value = (stored?.pageIndex ?: 0).coerceIn(0, lastPage.coerceAtLeast(0))
    }

    /**
     * Records where the reader has come to rest.
     *
     * Called when a scroll settles rather than as it moves: a fling through a
     * long book passes hundreds of pages the reader never saw, and storing each
     * of them would be hundreds of writes to arrive at the one page that
     * mattered.
     */
    fun onPageSettled(pageIndex: Int) {
        val document = _uiState.value.dataOrNull ?: return

        viewModelScope.launch {
            runCatchingApp {
                progressRepository.save(
                    location = location,
                    progress = ReadingProgress(
                        pageIndex = pageIndex,
                        pageCount = document.pages.pageCount
                    )
                )
            }
        }
    }

    /**
     * Read in the background as soon as the book is open, rather than when the
     * contents panel asks for it.
     *
     * On platforms whose PDF renderer has no outline of its own, this means
     * scanning the file - far too much to do while the reader waits with a panel
     * open, and far too much to do on the thread drawing the pages. Starting it
     * here means it is almost always ready before anyone looks.
     */
    private fun loadOutline(document: ReaderDocument) {
        viewModelScope.launch {
            val entries = withContext(Dispatchers.Default) {
                runCatchingApp { document.pages.outline?.asTableOfContents() }.getOrNull()
            }

            _outline.value = entries
                ?.let { ReaderOutline.Chapters(it) }
                ?: ReaderOutline.Unavailable
        }
    }

    fun retry() {
        closeDocument()
        _uiState.value = UiState.Loading
        _outline.value = ReaderOutline.Loading
        _initialPage.value = null
        open()
    }

    /**
     * The page at [index], drawn to fit [widthPx], or null if that page cannot
     * be drawn.
     *
     * Called once per page as it scrolls into view. Rendering runs off the main
     * thread inside the document itself; what this adds is the cache, without
     * which every page would be redrawn each time it scrolled back into view.
     */
    suspend fun page(index: Int, widthPx: Int): ImageBitmap? {
        val pages = _uiState.value.dataOrNull?.pages ?: return null
        if (index !in 0 until pages.pageCount) return null

        val targetWidthPx = renderWidthFor(widthPx)

        cached(index, targetWidthPx)?.let { return it }

        val rendered = pages.renderPage(index, targetWidthPx) ?: return null
        store(index, targetWidthPx, rendered)

        return rendered
    }

    private suspend fun cached(index: Int, widthPx: Int): ImageBitmap? = cacheLock.withLock {
        if (cachedWidthPx != widthPx) {
            renderedPages.clear()
            cachedWidthPx = widthPx
            return@withLock null
        }

        // Removing and re-inserting moves the entry to the end of the map, which
        // is what makes eviction least-recently-used rather than oldest-first.
        renderedPages.remove(index)?.also { renderedPages[index] = it }
    }

    private suspend fun store(index: Int, widthPx: Int, page: ImageBitmap) = cacheLock.withLock {
        // The window resized while this page was rendering, so it is already the
        // wrong size for the cache it would land in.
        if (cachedWidthPx != widthPx) return@withLock

        renderedPages[index] = page

        // Least-recently-used out first, until the budget is met - but never
        // below the handful around the page being read.
        while (renderedPages.size > MIN_CACHED_PAGES && cachedPixels() > MAX_CACHED_PIXELS) {
            renderedPages.remove(renderedPages.keys.first())
        }
    }

    private fun cachedPixels(): Long =
        renderedPages.values.sumOf { it.width.toLong() * it.height.toLong() }

    /**
     * The open document holds a file handle on the working copy, so leaving the
     * reader has to give it back rather than waiting for a finalizer.
     */
    override fun onCleared() {
        closeDocument()
    }

    private fun closeDocument() {
        _uiState.value.dataOrNull?.close()
        renderedPages.clear()
        cachedWidthPx = 0
    }
}
