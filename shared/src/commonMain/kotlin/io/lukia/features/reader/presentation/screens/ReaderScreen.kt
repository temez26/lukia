package io.lukia.features.reader.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.core.presentation.UiState
import io.lukia.core.presentation.dataOrNull
import io.lukia.features.reader.domain.PagedContent
import io.lukia.features.reader.domain.ReaderDocument
import io.lukia.features.reader.domain.ReaderOutline
import io.lukia.features.reader.domain.ReaderSettings
import io.lukia.features.reader.domain.ReflowableContent
import io.lukia.features.reader.presentation.ReaderCommand
import io.lukia.features.reader.presentation.ReaderViewModel
import io.lukia.features.reader.presentation.ShownTranslations
import io.lukia.features.reader.presentation.TranslateOnScreen
import io.lukia.features.reader.presentation.components.ReaderContentsPanel
import io.lukia.features.reader.presentation.components.ReaderSettingsPanel
import io.lukia.features.reader.presentation.components.readerToolbarHeight
import io.lukia.features.reader.presentation.readerCommandFor
import io.lukia.features.reader.presentation.theme.ReaderTheming
import io.lukia.features.reader.presentation.theme.readerPageStyle
import io.lukia.features.translate.domain.BulkTranslation
import io.lukia.features.translate.domain.PageSentence
import io.lukia.features.translate.domain.TextAnchor
import io.lukia.features.translate.domain.TranslationGestures
import io.lukia.features.translate.presentation.PassageSelection
import io.lukia.features.translate.presentation.PassageTranslation
import io.lukia.features.translate.presentation.ReaderTranslations
import io.lukia.shared.presentation.components.UiStateContent
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.Space
import io.lukia.shared.presentation.theme.isWideWindow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

internal val ChromeVerticalPadding: Dp = Space.md

/**
 * What the floating toolbar occupies, and so what the first page must clear.
 *
 * Derived from the bar rather than fixed, because the bar's own height follows
 * whether the window is being pointed at or touched.
 */
@Composable
internal fun chromeHeight(): Dp = readerToolbarHeight() + ChromeVerticalPadding * 2

/** The margin a section's text is set inside its sheet. */
internal val SheetPadding: Dp = 20.dp

@Composable
fun ReaderScreen(
    location: String,
    onBack: () -> Unit,
    viewModel: ReaderViewModel = koinViewModel(parameters = { parametersOf(location) }),
) {
    val uiState by viewModel.uiState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val outline by viewModel.outline.collectAsState()
    val initialPage by viewModel.initialPage.collectAsState()
    val translations by viewModel.translations.collectAsState()
    val translateOnScreen by viewModel.translateOnScreen.collectAsState()
    val translationConfigured by viewModel.translationConfigured.collectAsState()
    val shownTranslations by viewModel.shownTranslations.collectAsState()
    val gestures by viewModel.translationGestures.collectAsState()
    val bulkTranslation by viewModel.bulkTranslation.collectAsState()

    ReaderContent(
        uiState = uiState,
        settings = settings,
        outline = outline,
        initialPage = initialPage,
        translations = translations,
        translateOnScreen = translateOnScreen,
        translationConfigured = translationConfigured,
        shownTranslations = shownTranslations,
        gestures = gestures,
        bulkTranslation = bulkTranslation,
        onBack = onBack,
        onRetry = viewModel::retry,
        onSettingsChange = viewModel::updateSettings,
        onPageSettled = viewModel::onPageSettled,
        onPassageSelected = viewModel::onPassageSelected,
        onPassagesRequested = viewModel::onPassagesRequested,
        onTranslateOnScreen = viewModel::onTranslateOnScreen,
        onContinuousTranslationChange = viewModel::setContinuousTranslation,
        onTranslationsVisibleChange = viewModel::setTranslationsVisible,
        sentencesOnPage = viewModel::sentencesOn,
        renderPage = viewModel::page,
    )
}

/**
 * Stateless half of the screen. Split out so the layout can be rendered without
 * a ViewModel or dependency graph, matching the other screens.
 */
@Composable
fun ReaderContent(
    uiState: UiState<ReaderDocument>,
    settings: ReaderSettings,
    outline: ReaderOutline,
    /** Where to open, once it is known; null while the stored position is being read. */
    initialPage: Int? = null,
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
    /** Which of the two ways of translating more than one passage are offered at all. */
    bulkTranslation: BulkTranslation = BulkTranslation.Default,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSettingsChange: (ReaderSettings) -> Unit,
    onPageSettled: (pageIndex: Int) -> Unit = {},
    onPassageSelected: (PassageSelection) -> Unit = {},
    onPassagesRequested: (List<PassageSelection>) -> Unit = {},
    onTranslateOnScreen: () -> Unit = {},
    onContinuousTranslationChange: (Boolean) -> Unit = {},
    onTranslationsVisibleChange: (Boolean) -> Unit = {},
    /** What is written on a page of a fixed-layout document, read when one is looked at. */
    sentencesOnPage: suspend (index: Int) -> List<PageSentence> = { emptyList() },
    renderPage: suspend (index: Int, widthPx: Int) -> ImageBitmap?,
) {
    val dimens = LocalLukiaDimens.current
    val scope = rememberCoroutineScope()
    val chrome = rememberReaderChromeState()

    val compact = !isWideWindow
    val chromeHeight = chromeHeight()

    val translationUi = ReaderTranslationUi(
        configured = translationConfigured,
        onScreen = translateOnScreen,
        shown = shownTranslations,
        offered = bulkTranslation,
        onTranslateOnScreen = onTranslateOnScreen,
        onContinuousChange = onContinuousTranslationChange,
        onVisibleChange = onTranslationsVisibleChange,
    )

    // Filed by where each translation belongs, once per change, so that a
    // sentence changing state reaches the block or the page it is on and no
    // further. See ReaderTranslations.
    val translated = remember(translations) { ReaderTranslations.of(translations) }

    // The reader wears its own theme; the rest of the app keeps following the
    // system. Everything inside - the glass chrome, the type, the panel - picks
    // the reading colours up from the Material scheme without being passed them.
    ReaderTheming(theme = settings.theme) {
        val document = uiState.dataOrNull
        val pageStyle = readerPageStyle(settings.theme)
        // Created here rather than in the page list so that it survives the list
        // being replaced, and so the toolbar can read the page number from it.
        val listState = rememberLazyListState()

        // Reading puts the chrome away on a phone, where the bar is a tenth of
        // the screen and every scroll is a deliberate swipe. A wide window keeps
        // it: there the bar costs almost nothing, and a mouse wheel is not a
        // statement of intent - chrome that vanished on every notch of it would
        // be gone whenever the reader looked for it. It still goes away on a
        // click, which is a decision rather than a side effect.
        LaunchedEffect(listState, compact) {
            if (!compact) return@LaunchedEffect

            snapshotFlow { listState.isScrollInProgress }
                .filter { it }
                .collect { chrome.hideChrome() }
        }

        // Opening where the book was left off. Done as a scroll rather than as
        // the list's starting index because the stored page arrives after the
        // list does - the file has to be opened before its length is known, and
        // a page past the end is not a place to start. Nothing has been drawn
        // yet at this point, so there is no jump to see.
        LaunchedEffect(listState, initialPage) {
            if (initialPage != null && initialPage > 0) listState.scrollToItem(initialPage)
        }

        // Where the reader came to rest, once per stop rather than per page
        // passed. A fling crosses pages nobody read; the one they stopped on is
        // the one worth remembering.
        LaunchedEffect(listState, document) {
            if (document == null) return@LaunchedEffect

            snapshotFlow { listState.isScrollInProgress to listState.firstVisibleItemIndex }
                .filter { (scrolling, _) -> !scrolling }
                .map { (_, page) -> page }
                .distinctUntilChanged()
                .collect(onPageSettled)
        }

        val commands = readerCommands(
            listState = listState,
            unitCount = document?.content?.unitCount ?: 0,
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

        val focusRequester = remember { FocusRequester() }

        // The reader takes the keyboard as it opens, so the shortcuts work
        // without a click first. Focus moves off it the moment anything in the
        // chrome is tabbed to, and the handler below runs after that control has
        // had its say - so Space on a focused segment chooses it rather than
        // turning a page.
        LaunchedEffect(Unit) { focusRequester.requestFocus() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { event ->
                    val command = readerCommandFor(event) ?: return@onKeyEvent false

                    // Escape closes whatever is open on top of the book before
                    // it closes the book. Handled here rather than left to the
                    // panels' own back handler: that one answers to the
                    // platform's back gesture, which not every platform routes a
                    // key into, and "Escape leaves the book" firing under an
                    // open panel is not a mistake worth risking.
                    if (command == ReaderCommand.Close && chrome.panelOpen) {
                        chrome.closePanels()
                        return@onKeyEvent true
                    }

                    // A shortcut is a reason to show the chrome: it is how the
                    // reader sees that the page width changed, or which page
                    // they landed on.
                    chrome.showChrome()
                    commands(command)
                    true
                },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing),
            ) {
                UiStateContent(
                    state = uiState,
                    loadingMessage = "Opening…",
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

                        is ReflowableContent -> SectionList(
                            sections = content.sections,
                            listState = listState,
                            settings = settings,
                            pageColor = pageStyle.pageColor,
                            translations = translated,
                            onToggleChrome = chrome::toggleChrome,
                            onSettingsChange = onSettingsChange,
                            onPassageSelected = onPassageSelected,
                            onPassagesRequested = onPassagesRequested,
                            translateOnScreen = translateOnScreen,
                            gestures = gestures,
                        )
                    }
                }

                // Catches the press that closes an open panel, which is a
                // non-focusable popup and cannot see it.
                //
                // Covers the page and not the chrome, so that reaching straight
                // from one panel to the other is one press rather than two.
                // Deliberately not dimmed: the point of these panels is watching
                // the page change as they are used.
                if (chrome.panelOpen) {
                    DismissLayer(onDismiss = chrome::closePanels)
                }

                ReaderTopChrome(
                    chrome = chrome,
                    document = document,
                    listState = listState,
                    translation = translationUi,
                    onBack = onBack,
                    modifier = Modifier.align(Alignment.TopCenter),
                )

                // The panels are laid out in the reader's own window rather than
                // in popups of their own. A popup is a separate, non-focusable
                // window: keyboards and switch controls cannot reach into one,
                // and making it focusable instead means it swallows the press
                // that should have opened the other panel. In-window, both work.
                if (document != null) {
                    ReaderContentsPanel(
                        expanded = chrome.contentsExpanded,
                        listState = listState,
                        unitCount = document.content.unitCount,
                        unit = document.content.unit,
                        outline = outline,
                        onGoToPage = { pageIndex ->
                            scope.launch { listState.scrollToItem(pageIndex) }
                        },
                        onDismissRequest = chrome::closePanels,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = chromeHeight),
                    )
                }

                ReaderSettingsPanel(
                    expanded = chrome.settingsExpanded,
                    settings = settings,
                    // A PDF's type was set when the file was made.
                    typographyEnabled = document?.content is ReflowableContent,
                    onSettingsChange = onSettingsChange,
                    onDismissRequest = chrome::closePanels,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = chromeHeight, end = dimens.screenPadding),
                )
            }
        }
    }
}
