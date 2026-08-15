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
import io.lukia.features.reader.domain.ReaderSettings
import io.lukia.features.reader.infrastructure.ReaderSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Ceiling on how wide a page is ever drawn, whatever the window does. Beyond
 * this the extra pixels are past what a page's own detail supports, while the
 * memory each one costs is not: a page is four bytes per pixel and the reader
 * holds several.
 */
private const val MAX_RENDER_WIDTH_PX = 1600

/**
 * How many rendered pages are kept beyond the ones on screen. Enough to scroll
 * back a page or two without waiting; small enough that a long book does not
 * accumulate every page it has been shown.
 */
private const val MAX_CACHED_PAGES = 4

class ReaderViewModel(
    private val location: String,
    private val openDocumentUseCase: OpenDocumentUseCase,
    private val settingsRepository: ReaderSettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<ReaderDocument>>(UiState.Loading)
    val uiState: StateFlow<UiState<ReaderDocument>> = _uiState.asStateFlow()

    /**
     * Exposed straight from the repository rather than copied into this
     * ViewModel: the settings outlive the open book, and mirroring them here
     * would be one more thing that could disagree with what was stored.
     */
    val settings: StateFlow<ReaderSettings> = settingsRepository.settings

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
            _uiState.value = runCatchingApp { openDocumentUseCase(location).getOrThrow() }
                .fold(
                    onSuccess = { UiState.Success(it) },
                    onFailure = { UiState.Failure(it.toAppError()) }
                )
        }
    }

    fun retry() {
        closeDocument()
        _uiState.value = UiState.Loading
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
        val targetWidthPx = widthPx.coerceIn(1, MAX_RENDER_WIDTH_PX)

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

        while (renderedPages.size > MAX_CACHED_PAGES) {
            renderedPages.remove(renderedPages.keys.first())
        }
    }

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
