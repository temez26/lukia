package io.lukia.features.reader.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import io.lukia.core.domain.reading.ReadingProgress
import io.lukia.core.presentation.UiState
import io.lukia.core.presentation.dataOrNull
import io.lukia.features.reader.domain.PagedContent
import io.lukia.features.reader.domain.ReaderDocument
import io.lukia.features.reader.domain.ReaderSettings
import io.lukia.features.reader.domain.ReflowableContent
import io.lukia.features.reader.domain.outline.ReaderOutline
import io.lukia.features.reader.domain.page.PageSentence
import io.lukia.features.reader.presentation.KeepScreenAwake
import io.lukia.features.reader.presentation.components.chrome.ReaderNotice
import io.lukia.features.reader.presentation.pagination.PaginatedBook
import io.lukia.features.reader.presentation.pagination.onPages
import io.lukia.features.reader.presentation.screens.input.readerCommands
import io.lukia.features.reader.presentation.screens.input.readerKeys
import io.lukia.features.reader.presentation.screens.pages.PageList
import io.lukia.features.reader.presentation.screens.pages.TextPageList
import io.lukia.features.reader.presentation.screens.translation.DismissLayer
import io.lukia.features.reader.presentation.screens.translation.ReaderTranslationPreferences
import io.lukia.features.reader.presentation.screens.translation.ReaderTranslationSettings
import io.lukia.features.reader.presentation.screens.translation.ReaderTranslationUi
import io.lukia.features.reader.presentation.theme.ReaderTheming
import io.lukia.features.reader.presentation.theme.readerPageStyle
import io.lukia.features.reader.presentation.translation.ShownTranslations
import io.lukia.features.reader.presentation.translation.TranslateOnScreen
import io.lukia.features.translate.domain.BulkTranslation
import io.lukia.features.translate.domain.settings.TargetLanguage
import io.lukia.features.translate.domain.settings.TranslationGestures
import io.lukia.features.translate.domain.text.TextAnchor
import io.lukia.features.translate.presentation.BookTranslations
import io.lukia.features.translate.presentation.PassageSelection
import io.lukia.features.translate.presentation.PassageTranslation
import io.lukia.shared.presentation.components.state.UiStateContent
import io.lukia.shared.presentation.theme.isWideWindow

/**
 * Stateless half of the screen. Split out so the layout can be rendered without
 * a ViewModel or dependency graph, matching the other screens.
 *
 * The feature's public surface is [ReaderScreen]; this is reached only from
 * there and from the tests that render a reader without a graph behind it.
 */
@Composable
internal fun ReaderContent(
    uiState: UiState<ReaderDocument>,
    settings: ReaderSettings,
    outline: ReaderOutline,
    /**
     * Where the book was left off and how long it was then, or null while the
     * stored position is still being read.
     *
     * Both halves, because a reflowable book's length is a fact about the window
     * it was read in: the same book on a phone and on a monitor is a different
     * number of pages, so a stored page number only means anything alongside the
     * count it was one of.
     */
    initialPosition: ReadingProgress? = null,
    /** What has become of each sentence somebody has asked about, by where it is. */
    translations: Map<TextAnchor, PassageTranslation> = emptyMap(),
    /** Whether what the reader can see should be translated without being tapped. */
    translateOnScreen: TranslateOnScreen = TranslateOnScreen.Idle,
    /** Whether there is anything set up to translate with, which is what the controls need. */
    translationConfigured: Boolean = false,
    /** What the reader's own translations are doing, for the control that puts them away. */
    shownTranslations: ShownTranslations = ShownTranslations.None,
    /** What a tap and a long press on the book ask for. */
    gestures: TranslationGestures = TranslationGestures.Default,
    /** The language a passage comes back in, which the panel can also change. */
    targetLanguage: TargetLanguage = TargetLanguage.Default,
    /** Which of the two ways of translating more than one passage are offered at all. */
    bulkTranslation: BulkTranslation = BulkTranslation.Default,
    /** How this book is being read, and what became of asking for it as text. */
    layout: ReaderLayoutUi = ReaderLayoutUi(),
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSettingsChange: (ReaderSettings) -> Unit,
    onPageSettled: (pageIndex: Int, pageCount: Int) -> Unit = { _, _ -> },
    onPassageSelected: (PassageSelection) -> Unit = {},
    onPassagesRequested: (List<PassageSelection>) -> Unit = {},
    onTranslateOnScreen: () -> Unit = {},
    onContinuousTranslationChange: (Boolean) -> Unit = {},
    onTranslationsVisibleChange: (Boolean) -> Unit = {},
    onTargetLanguageChange: (TargetLanguage) -> Unit = {},
    onTranslationGesturesChange: (TranslationGestures) -> Unit = {},
    /** What is written on a page of a fixed-layout document, read when one is looked at. */
    sentencesOnPage: suspend (index: Int) -> List<PageSentence> = { emptyList() },
    renderPage: suspend (index: Int, widthPx: Int) -> ImageBitmap?,
) {
    val chrome = rememberReaderChromeState()

    val compact = !isWideWindow

    val translationUi = ReaderTranslationUi(
        configured = translationConfigured,
        onScreen = translateOnScreen,
        shown = shownTranslations,
        offered = bulkTranslation,
        onTranslateOnScreen = onTranslateOnScreen,
        onContinuousChange = onContinuousTranslationChange,
        onVisibleChange = onTranslationsVisibleChange,
    )

    val translationPreferences = ReaderTranslationPreferences(
        targetLanguage = targetLanguage,
        gestures = gestures,
        onTargetLanguageChange = onTargetLanguageChange,
        onGesturesChange = onTranslationGesturesChange,
    )

    // Filed by where each translation belongs, once per change, so that a
    // sentence changing state reaches the block or the page it is on and no
    // further. See BookTranslations.
    val translated = remember(translations) { BookTranslations.of(translations) }

    // The reader wears its own theme; the rest of the app keeps following the
    // system. Everything inside - the glass chrome, the type, the panel - picks
    // the reading colours up from the Material scheme without being passed them.
    ReaderTheming(theme = settings.theme) {
        val document = uiState.dataOrNull
        val pageStyle = readerPageStyle(settings.theme)

        // Only with a book actually open: a reader waiting on a file that will
        // not open, or looking at the error from one, is not reading.
        KeepScreenAwake(enabled = settings.keepScreenAwake && document != null)

        // Created here rather than in the page list so that it survives the list
        // being replaced, and so the toolbar can read the page number from it.
        val listState = rememberLazyListState()

        HideChromeWhileScrolling(listState = listState, compact = compact, chrome = chrome)

        // How long the open book is in pages. A PDF says so itself; a reflowable
        // one is only as long as the window it has been laid out for, which is
        // settled by the page list and reported back here.
        var paginated by remember(document) { mutableStateOf(PaginatedBook.Empty) }

        val pageCount = when (document?.content) {
            is PagedContent -> document.content.partCount
            is ReflowableContent -> paginated.pageCount
            null -> 0
        }

        // The contents panel points at sections; the reader turns pages. For a
        // reflowable book the two are only related through the layout, so the
        // entries are moved onto it here rather than every row working it out.
        val pagedOutline = remember(outline, paginated) {
            outline.onPages(paginated, reflowable = document?.content is ReflowableContent)
        }

        KeepReadingPosition(
            listState = listState,
            document = document,
            initialPosition = initialPosition,
            pageCount = pageCount,
            onPageSettled = onPageSettled,
        )

        val commands = readerCommands(
            listState = listState,
            pageCount = pageCount,
            // The keys that widen a PDF's page are the keys that enlarge an
            // EPUB's type: both are "make this easier to read", and which
            // setting that means depends on which kind of book is open.
            reflowable = document?.content is ReflowableContent,
            settings = settings,
            onSettingsChange = onSettingsChange,
            onBack = onBack,
            onToggleContents = chrome::toggleContents,
            onToggleSettings = chrome::toggleSettings,
            // A shortcut for something the reader has not asked to be offered
            // does nothing, exactly as its button does not appear. Off is off
            // wherever it is reached from.
            onTranslateOnScreen = { if (bulkTranslation.onScreen) onTranslateOnScreen() },
            onToggleContinuousTranslation = {
                if (bulkTranslation.asYouRead) {
                    onContinuousTranslationChange(!translateOnScreen.continuous)
                }
            },
            onToggleTranslationsShown = {
                if (shownTranslations.any) onTranslationsVisibleChange(!shownTranslations.visible)
            },
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .readerKeys(chrome = chrome, onCommand = commands),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing),
            ) {
                UiStateContent(
                    state = uiState,
                    // Reading a PDF's text is the one opening long enough to
                    // count: the whole file is read before the first page can
                    // be laid out.
                    loadingMessage = layout.loadingMessage,
                    // Opening can fail on something worth a second attempt - a
                    // copy that could not be written, a file being read as it is
                    // moved.
                    actionLabel = "Try again",
                    onAction = onRetry,
                    modifier = Modifier.fillMaxSize(),
                ) { openDocument ->
                    when (val content = openDocument.content) {
                        is PagedContent -> PageList(
                            pages = content,
                            listState = listState,
                            pageScale = settings.pageScale,
                            pageColor = pageStyle.pageColor,
                            pageFilter = pageStyle.pageFilter,
                            translations = translated,
                            onToggleChrome = chrome::toggleChrome,
                            onPageScaleChange = { onSettingsChange(settings.withPageScale(it)) },
                            onPassageSelected = onPassageSelected,
                            onPassagesRequested = onPassagesRequested,
                            translateOnScreen = translateOnScreen,
                            gestures = gestures,
                            sentencesOnPage = sentencesOnPage,
                            renderPage = renderPage,
                        )

                        is ReflowableContent -> TextPageList(
                            sections = content.sections,
                            listState = listState,
                            settings = settings,
                            pageColor = pageStyle.pageColor,
                            translations = translated,
                            onToggleChrome = chrome::toggleChrome,
                            onSettingsChange = onSettingsChange,
                            onPassageSelected = onPassageSelected,
                            onPassagesRequested = onPassagesRequested,
                            onPagesChange = { paginated = it },
                            translateOnScreen = translateOnScreen,
                            gestures = gestures,
                            openedAt = initialPosition,
                        )
                    }
                }

                // Catches the press that closes an open panel, which is a
                // non-focusable popup and cannot see it.
                //
                // Under the chrome and over the page, so that reaching straight
                // from one panel to the other is one press rather than two.
                // Deliberately not dimmed: the point of these panels is watching
                // the page change as they are used.
                if (chrome.panelOpen) {
                    DismissLayer(onDismiss = chrome::closePanels)
                }

                ReaderTopChrome(
                    chrome = chrome,
                    document = document,
                    pageCount = pageCount,
                    outline = pagedOutline,
                    listState = listState,
                    translation = translationUi,
                    onBack = onBack,
                    modifier = Modifier.align(Alignment.TopCenter),
                )

                // Under the panels and over the page: it is a fact about the
                // book rather than a control, and nothing it can be behind ever
                // reaches the bottom edge.
                ReaderBottomChrome(
                    chrome = chrome,
                    pageCount = pageCount,
                    listState = listState,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )

                ReaderPanels(
                    chrome = chrome,
                    document = document,
                    pageCount = pageCount,
                    outline = pagedOutline,
                    listState = listState,
                    settings = settings,
                    layout = layout,
                    onSettingsChange = onSettingsChange,
                    translationSection = {
                        ReaderTranslationSettings(translationUi, translationPreferences)
                    },
                )

                // Over everything, because it is about the book somebody is
                // looking at rather than about anything in the chrome.
                ReaderNotice(
                    text = "This PDF has no text in it to re-set - its pages are " +
                        "pictures. Showing it as pages.",
                    visible = layout.noTextLayer,
                    onDismiss = layout.onNoticeDismiss,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}
