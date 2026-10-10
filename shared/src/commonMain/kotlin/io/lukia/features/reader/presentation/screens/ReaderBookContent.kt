package io.lukia.features.reader.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import io.lukia.features.reader.presentation.components.translation.WordLookupPanel
import io.lukia.features.reader.presentation.pagination.PaginatedBook
import io.lukia.features.reader.presentation.pagination.onPages
import io.lukia.features.reader.presentation.screens.input.readerCommands
import io.lukia.features.reader.presentation.screens.input.readerKeys
import io.lukia.features.reader.presentation.screens.pages.PageList
import io.lukia.features.reader.presentation.screens.pages.TextPageList
import io.lukia.features.reader.presentation.screens.translation.DismissLayer
import io.lukia.features.reader.presentation.screens.translation.ReaderTranslating
import io.lukia.features.reader.presentation.screens.translation.ReaderTranslatingActions
import io.lukia.features.reader.presentation.screens.translation.ReaderTranslationPreferences
import io.lukia.features.reader.presentation.screens.translation.ReaderTranslationSettings
import io.lukia.features.reader.presentation.screens.translation.ReaderTranslationUi
import io.lukia.features.reader.presentation.theme.ReaderTheming
import io.lukia.features.reader.presentation.theme.readerPageStyle
import io.lukia.features.reader.presentation.translation.BookTranslations
import io.lukia.resources.Res
import io.lukia.resources.action_try_again
import io.lukia.resources.reader_pdf_no_text
import io.lukia.shared.presentation.components.state.UiStateContent
import io.lukia.shared.presentation.components.surface.behindOverlay
import io.lukia.shared.presentation.theme.isWideWindow
import org.jetbrains.compose.resources.stringResource

/**
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
    /** What translating has come to in this book, and how it is set up. */
    translating: ReaderTranslating = ReaderTranslating(),
    translatingActions: ReaderTranslatingActions = ReaderTranslatingActions(),
    /** How this book is being read, and what became of asking for it as text. */
    layout: ReaderLayoutUi = ReaderLayoutUi(),
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSettingsChange: (ReaderSettings) -> Unit,
    onPageSettled: (pageIndex: Int, pageCount: Int) -> Unit = { _, _ -> },
    /** What is written on a page of a fixed-layout document, read when one is looked at. */
    sentencesOnPage: suspend (index: Int) -> List<PageSentence> = { emptyList() },
    renderPage: suspend (index: Int, widthPx: Int) -> ImageBitmap?,
) {
    val chrome = rememberReaderChromeState()

    val compact = !isWideWindow

    val translationUi = ReaderTranslationUi(
        onScreen = translating.onScreen,
        shown = translating.shown,
        offered = translating.offered,
        onTranslateOnScreen = translatingActions.onTranslateOnScreen,
        onContinuousChange = translatingActions.onContinuousChange,
        onVisibleChange = translatingActions.onVisibleChange,
    )

    // What a gesture actually asks for, which is not always what it is set to.
    // Nothing but a translation service answers a sentence or a paragraph, so
    // with none set up - or AI switched off - a tap asks for the word and a hold
    // asks for nothing, rather than for something that can only fail.
    //
    // The page gets this and the panel below gets the setting itself, because
    // the panel is where the reader changes it: shown the clamped value, its
    // switch would write the clamp back and quietly lose the unit they chose.
    val gesturesInForce = remember(translating.gestures, translating.serviceConfigured) {
        val gestures = translating.gestures

        if (translating.serviceConfigured) gestures else gestures.withoutAModel()
    }

    val translationPreferences = ReaderTranslationPreferences(
        targetLanguage = translating.targetLanguage,
        gestures = translating.gestures,
        notes = translating.notes,
        onTargetLanguageChange = translatingActions.onTargetLanguageChange,
        onGesturesChange = translatingActions.onGesturesChange,
        onNotesChange = translatingActions.onNotesChange,
    )

    // Filed by where each translation belongs, once per change, so that a
    // sentence changing state reaches the block or the page it is on and no
    // further. See BookTranslations, which is also where the translator's asides
    // are dropped for a reader who has turned them off.
    val translated = remember(translating.passages, translating.notes) {
        BookTranslations.of(translating.passages, translating.notes)
    }

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
        //
        // A new one when the book stops being pages and becomes text, or the
        // other way round. The two are different lengths, and a list still
        // sitting on page 702 of the text was a list past the end of the file's
        // own 472 pages - which opened it at the last one and stored that.
        val listState = rememberSaveable(document?.content is PagedContent, saver = LazyListState.Saver) {
            LazyListState()
        }

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
            // A fixed-layout document is as long as it will ever be the moment
            // it opens. A reflowable one is only as long as it has been
            // measured so far, and [pageCount] climbs until it is done.
            laidOut = document?.content !is ReflowableContent || paginated.complete,
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
            onTranslateOnScreen = {
                if (translating.offered.onScreen) translatingActions.onTranslateOnScreen()
            },
            onToggleContinuousTranslation = {
                if (translating.offered.asYouRead) {
                    translatingActions.onContinuousChange(!translating.onScreen.continuous)
                }
            },
            onToggleTranslationsShown = {
                if (translating.shown.any) {
                    translatingActions.onVisibleChange(!translating.shown.visible)
                }
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
                // Everything the word panel opens over, put out of reach while
                // it is - see [behindOverlay]. The panel dims the page and its
                // scrim catches the pointer, which is where its modality used to
                // stop: Tab walked through the dimming into the chrome behind,
                // and a screen reader's swipe-next carried on into the book.
                //
                // Wrapped rather than given a modifier, for the reason
                // `LibraryScreen` wraps its own: the panel is a sibling of all
                // this, and a composable cannot quieten the ones beside it.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .behindOverlay(translating.wordInquiry != null),
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
                        actionLabel = stringResource(Res.string.action_try_again),
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
                                onPassageSelected = translatingActions.onPassageSelected,
                                onPassageHold = translatingActions.onPassageHold,
                                onPassageInquiry = translatingActions.onPassageInquiry,
                                onPassagesRequested = translatingActions.onPassagesRequested,
                                translateOnScreen = translating.onScreen,
                                gestures = gesturesInForce,
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
                                onPassageSelected = translatingActions.onPassageSelected,
                                onPassageHold = translatingActions.onPassageHold,
                                onPassageInquiry = translatingActions.onPassageInquiry,
                                onPassagesRequested = translatingActions.onPassagesRequested,
                                onPagesChange = { paginated = it },
                                translateOnScreen = translating.onScreen,
                                gestures = gesturesInForce,
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
                            ReaderTranslationSettings(
                                translation = translationUi,
                                preferences = translationPreferences,
                                serviceConfigured = translating.serviceConfigured,
                            )
                        },
                    )
                }

                // Over the panels and the chrome alike: it was opened from the
                // page, it dims what is behind it, and nothing in the chrome is
                // reachable while the reader is reading about one word.
                WordLookupPanel(
                    inquiry = translating.wordInquiry,
                    onDismiss = translatingActions.onWordInquiryDismiss,
                    onAction = translatingActions.onInquiredWordAction,
                )

                // Over everything, because it is about the book somebody is
                // looking at rather than about anything in the chrome.
                ReaderNotice(
                    text = stringResource(Res.string.reader_pdf_no_text),
                    visible = layout.noTextLayer,
                    onDismiss = layout.onNoticeDismiss,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}
