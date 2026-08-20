package io.lukia.features.reader.presentation

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.lukia.core.domain.runCatchingApp
import io.lukia.core.domain.toAppError
import io.lukia.core.presentation.UiState
import io.lukia.core.presentation.dataOrNull
import io.lukia.core.presentation.displayTitle
import io.lukia.features.reader.application.OpenDocumentUseCase
import io.lukia.features.reader.domain.PagedContent
import io.lukia.features.reader.domain.ReaderDocument
import io.lukia.features.reader.domain.ReaderOutline
import io.lukia.features.reader.domain.ReaderSettings
import io.lukia.features.reader.domain.ReadingProgress
import io.lukia.features.reader.domain.ReadingProgressRepository
import io.lukia.features.reader.domain.asTableOfContents
import io.lukia.features.reader.domain.chapterCovering
import io.lukia.features.reader.infrastructure.ReaderSettingsRepository
import io.lukia.features.translate.application.TranslatePassageUseCase
import io.lukia.features.translate.domain.BulkTranslation
import io.lukia.features.translate.domain.PageSentence
import io.lukia.features.translate.domain.TextAnchor
import io.lukia.features.translate.domain.TranslationGestures
import io.lukia.features.translate.domain.TranslationRequest
import io.lukia.features.translate.domain.asTranslationContext
import io.lukia.features.translate.domain.pageSentences
import io.lukia.features.translate.infrastructure.TranslationSettingsRepository
import io.lukia.features.translate.presentation.PassageSelection
import io.lukia.features.translate.presentation.PassageTranslation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReaderViewModel(
    private val location: String,
    private val openDocumentUseCase: OpenDocumentUseCase,
    private val settingsRepository: ReaderSettingsRepository,
    private val progressRepository: ReadingProgressRepository,
    translatePassage: TranslatePassageUseCase,
    private val translationSettings: TranslationSettingsRepository,
) : ViewModel() {

    /**
     * Everything about translating what is on the page. Held here so the reader
     * screen sees one object, but implemented apart - see [ReaderTranslations].
     */
    private val translation = ReaderTranslations(translatePassage, translationSettings, viewModelScope)

    val translations: StateFlow<Map<TextAnchor, PassageTranslation>> = translation.passages
    val bulkTranslation: StateFlow<BulkTranslation> = translation.bulkTranslation
    val translateOnScreen: StateFlow<TranslateOnScreen> = translation.onScreen
    val shownTranslations: StateFlow<ShownTranslations> = translation.shown
    val translationGestures: StateFlow<TranslationGestures> = translation.gestures
    val translationConfigured: StateFlow<Boolean> = translation.configured

    private val _uiState = MutableStateFlow<UiState<ReaderDocument>>(UiState.Loading)
    val uiState: StateFlow<UiState<ReaderDocument>> = _uiState.asStateFlow()

    /**
     * Where to open, once it is known - null while the book is still being
     * opened and the stored position still being read.
     *
     * The length the position was stored against travels with it, because a
     * reflowable book's pages are the reader's own: the same book is a different
     * number of pages in a different window, and a page number only says where
     * somebody was alongside the count it was one of.
     *
     * Settled once per opening and never changed afterwards, so the screen can
     * treat it as an instruction to carry out rather than a position to keep
     * following. A value that tracked the reader would send them back to where
     * they started every time it arrived.
     */
    private val _initialPosition = MutableStateFlow<ReadingProgress?>(null)
    val initialPosition: StateFlow<ReadingProgress?> = _initialPosition.asStateFlow()

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

    private val renderedPages = RenderedPageCache()

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
                    onFailure = { UiState.Failure(it.toAppError()) },
                )

            _uiState.value = state

            if (state is UiState.Success) {
                restorePosition(state.data)
                loadOutline(state.data)
            }
        }
    }

    /**
     * Where this book was left off, or its beginning if it has never been
     * opened - which is the same instruction, so an unread book is not a case
     * of its own.
     *
     * A book nobody has opened is given a length of nothing rather than the
     * document's own count. For a PDF the two would be the same; for a
     * reflowable book the count here is a page count, and what the document can
     * say is how many divisions it was written in - a different number in a
     * different unit, and one this must not pass off as the other. Nothing is
     * the honest answer, and the reader opens at the beginning either way.
     */
    private suspend fun restorePosition(document: ReaderDocument) {
        val stored = runCatchingApp { progressRepository.progressFor(location) }.getOrNull()
        val content = document.content

        _initialPosition.value = when {
            stored == null -> ReadingProgress(pageIndex = 0, pageCount = 0)

            // A fixed-layout document's pages are its own, so a stored page past
            // the end of one is a file that has been replaced by a shorter one
            // under the same name - and no place to open at. A reflowable book
            // has no pages until it has been laid out for a window, so there is
            // nothing here to clamp it against; the page list does that, against
            // the layout it just made.
            content is PagedContent -> ReadingProgress(
                pageIndex = stored.pageIndex.coerceIn(0, (content.partCount - 1).coerceAtLeast(0)),
                pageCount = content.partCount,
            )

            else -> stored
        }
    }

    /**
     * Records where the reader has come to rest.
     *
     * Called when a scroll settles rather than as it moves: a fling through a
     * long book passes hundreds of pages the reader never saw, and storing each
     * of them would be hundreds of writes to arrive at the one page that
     * mattered.
     */
    fun onPageSettled(pageIndex: Int, pageCount: Int) {
        // Nothing to remember about a book that never opened, and a length of
        // nothing is a book that has not been laid out yet rather than one that
        // has been read to the end of.
        if (_uiState.value.dataOrNull == null || pageCount <= 0) return

        viewModelScope.launch {
            runCatchingApp {
                progressRepository.save(
                    location = location,
                    progress = ReadingProgress(pageIndex = pageIndex, pageCount = pageCount),
                )
            }
        }
    }

    private val pageSentences = PageSentenceCache()

    /**
     * What is written on page [index], or an empty list when nothing is - a
     * scan, a page that is one photograph, or a platform that cannot read one.
     *
     * All of those reach the reader the same way, which is that tapping the page
     * does nothing. None of them is worth telling somebody about.
     */
    suspend fun sentencesOn(index: Int): List<PageSentence> {
        val pages = (_uiState.value.dataOrNull?.content as? PagedContent)?.pages ?: return emptyList()

        return pageSentences.sentencesOn(index) {
            val glyphs = runCatchingApp { pages.pageText(index) }.getOrNull()
            glyphs?.let { pageSentences(it) }.orEmpty()
        }
    }

    /** A passage the reader pointed at. */
    fun onPassageSelected(selection: PassageSelection) {
        val document = _uiState.value.dataOrNull ?: return

        translation.onPassageTapped(selection.anchor) { document.requestFor(selection) }
    }

    /**
     * Every passage the reader can see, translated in one go.
     *
     * Asked for rather than tapped, so a sentence they have already flipped back
     * to the original stays as they left it and a failure is not retried behind
     * their back - see
     * [io.lukia.features.translate.presentation.TranslationSession.onPassageRequested].
     */
    fun onPassagesRequested(selections: List<PassageSelection>) {
        val document = _uiState.value.dataOrNull ?: return

        selections.forEach { selection ->
            translation.onPassageRequested(selection.anchor) {
                document.requestFor(selection)
            }
        }
    }

    fun onTranslateOnScreen() {
        translation.requestOnScreen()
    }

    fun setTranslationsVisible(visible: Boolean) {
        translation.showTranslations(visible)
    }

    fun setContinuousTranslation(continuous: Boolean) {
        translation.setContinuous(continuous)
    }

    /**
     * The book is read here rather than at the tap because this is where it is
     * known: the screen has the words and where they sit, and only the open
     * document can say which book they came out of and what it is about. That
     * is the whole of how a title and a genre reach a translator.
     */
    private fun ReaderDocument.requestFor(selection: PassageSelection) = TranslationRequest(
        text = selection.passage,
        targetLanguage = translation.targetLanguage,
        context = info.metadata.asTranslationContext(
            bookTitle = info.displayTitle(),
            chapterTitle = selection.chapterTitle ?: chapterOf(selection.anchor),
            surroundingText = selection.surroundingText,
        ),
        unit = selection.unit,
    )

    /**
     * What a page sits under, for a document whose sentences carry no chapter of
     * their own.
     *
     * A reflowable book's section names itself, so the screen already knows. A
     * page of a PDF does not - but the book's own table of contents does, and it
     * has been read and is sitting here already.
     */
    private fun chapterOf(anchor: TextAnchor): String? {
        if (anchor !is TextAnchor.Page) return null

        val chapters = (_outline.value as? ReaderOutline.Chapters)?.entries ?: return null

        return chapters.chapterCovering(anchor.pageIndex)
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
                runCatchingApp { document.content.outline?.asTableOfContents() }.getOrNull()
            }

            _outline.value = entries
                ?.let { ReaderOutline.Chapters(it) }
                ?: ReaderOutline.Unavailable
        }
    }

    fun retry() {
        closeDocument()
        translation.clear()
        pageSentences.clear()
        _uiState.value = UiState.Loading
        _outline.value = ReaderOutline.Loading
        _initialPosition.value = null
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
        // Only a fixed-layout document has pages to draw. A reflowable one is
        // set by the reader itself and never comes through here.
        val pages = (_uiState.value.dataOrNull?.content as? PagedContent)?.pages ?: return null
        if (index !in 0 until pages.pageCount) return null

        val targetWidthPx = renderWidthFor(widthPx)

        return renderedPages.page(index, targetWidthPx) {
            pages.renderPage(index, targetWidthPx)
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
    }
}
