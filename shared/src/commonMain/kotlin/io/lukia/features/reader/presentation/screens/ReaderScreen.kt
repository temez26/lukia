package io.lukia.features.reader.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.core.presentation.UiState
import io.lukia.core.presentation.dataOrNull
import io.lukia.core.presentation.displayTitle
import io.lukia.features.reader.domain.PagedContent
import io.lukia.features.reader.domain.ReaderDocument
import io.lukia.features.reader.domain.ReaderOutline
import io.lukia.features.reader.domain.ReaderSettings
import io.lukia.features.reader.domain.ReflowableContent
import io.lukia.features.reader.domain.TextSection
import io.lukia.features.reader.presentation.PAGE_SCALE_STEP
import io.lukia.features.reader.presentation.ReaderCommand
import io.lukia.features.reader.presentation.ReaderViewModel
import io.lukia.features.reader.presentation.SCROLL_NUDGE_FRACTION
import io.lukia.features.reader.presentation.components.PageIndicatorButton
import io.lukia.features.reader.presentation.readerCommandFor
import io.lukia.features.reader.presentation.components.ReaderContentsPanel
import io.lukia.features.reader.presentation.components.ReaderSettingsPanel
import io.lukia.features.reader.presentation.components.ReaderToolbar
import io.lukia.features.reader.presentation.components.TextSectionView
import io.lukia.features.reader.presentation.components.TextSettingsGlyph
import io.lukia.features.reader.presentation.components.ToolbarButton
import io.lukia.features.reader.presentation.renderWidthFor
import io.lukia.features.reader.presentation.theme.ReaderTheming
import io.lukia.features.reader.presentation.theme.readerPageStyle
import io.lukia.features.reader.presentation.theme.readerTextStyle
import io.lukia.shared.presentation.components.ShortcutTooltip
import io.lukia.shared.presentation.components.UiStateContent
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.LocalWindowWidthClass
import io.lukia.shared.presentation.theme.WindowWidthClass
import io.lukia.shared.presentation.theme.topChromeIn
import io.lukia.shared.presentation.theme.topChromeOut
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Gap between pages, which is what makes them read as separate sheets. */
private val PageGap: Dp = 12.dp

/** Breathing room at the end of the book. */
private val PageListPadding: Dp = 8.dp

private val ChromeVerticalPadding: Dp = 8.dp

/** What the floating toolbar occupies, and so what the first page must clear. */
private val ChromeHeight: Dp = 60.dp

/** The margin a section's text is set inside its sheet. */
private val SheetPadding: Dp = 22.dp

@Composable
fun ReaderScreen(
    location: String,
    onBack: () -> Unit,
    viewModel: ReaderViewModel = koinViewModel(parameters = { parametersOf(location) })
) {
    val uiState by viewModel.uiState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val outline by viewModel.outline.collectAsState()
    val initialPage by viewModel.initialPage.collectAsState()

    ReaderContent(
        uiState = uiState,
        settings = settings,
        outline = outline,
        initialPage = initialPage,
        onBack = onBack,
        onRetry = viewModel::retry,
        onSettingsChange = viewModel::updateSettings,
        onPageSettled = viewModel::onPageSettled,
        renderPage = viewModel::page
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
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSettingsChange: (ReaderSettings) -> Unit,
    onPageSettled: (pageIndex: Int) -> Unit = {},
    renderPage: suspend (index: Int, widthPx: Int) -> ImageBitmap?
) {
    val dimens = LocalLukiaDimens.current
    val scope = rememberCoroutineScope()
    var settingsExpanded by remember { mutableStateOf(false) }
    var contentsExpanded by remember { mutableStateOf(false) }
    var chromeVisible by remember { mutableStateOf(true) }

    val compact = LocalWindowWidthClass.current == WindowWidthClass.Compact
    val panelOpen = settingsExpanded || contentsExpanded

    // A panel hangs off the toolbar, so the toolbar cannot leave while one is
    // open without stranding it.
    val chromeShown = chromeVisible || panelOpen

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
                .collect { chromeVisible = false }
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
            onToggleContents = {
                settingsExpanded = false
                contentsExpanded = !contentsExpanded
            },
            onToggleSettings = {
                contentsExpanded = false
                settingsExpanded = !settingsExpanded
            }
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
                    if (command == ReaderCommand.Close && panelOpen) {
                        settingsExpanded = false
                        contentsExpanded = false
                        return@onKeyEvent true
                    }

                    // A shortcut is a reason to show the chrome: it is how the
                    // reader sees that the page width changed, or which page
                    // they landed on.
                    chromeVisible = true
                    commands(command)
                    true
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                UiStateContent(
                    state = uiState,
                    loadingMessage = "Opening…",
                    // Opening can fail on something worth a second attempt - a
                    // copy that could not be written, a file being read as it is
                    // moved.
                    actionLabel = "Try again",
                    onAction = onRetry,
                    modifier = Modifier.fillMaxSize()
                ) { openDocument ->
                    when (val content = openDocument.content) {
                        is PagedContent -> PageList(
                            pages = content,
                            listState = listState,
                            pageScale = settings.pageScale,
                            pageColor = pageStyle.pageColor,
                            pageFilter = pageStyle.pageFilter,
                            onToggleChrome = { chromeVisible = !chromeVisible },
                            onPageScaleChange = { onSettingsChange(settings.withPageScale(it)) },
                            renderPage = renderPage
                        )

                        is ReflowableContent -> SectionList(
                            sections = content.sections,
                            listState = listState,
                            settings = settings,
                            pageColor = pageStyle.pageColor,
                            onToggleChrome = { chromeVisible = !chromeVisible },
                            onSettingsChange = onSettingsChange
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
                if (panelOpen) {
                    DismissLayer(
                        onDismiss = {
                            settingsExpanded = false
                            contentsExpanded = false
                        }
                    )
                }

                // Floats over the page rather than taking a row of its own, so
                // that putting it away gives the book the whole screen without
                // re-laying out a single page.
                AnimatedVisibility(
                    visible = chromeShown,
                    enter = topChromeIn(),
                    exit = topChromeOut(),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    ReaderToolbar(
                        onBack = onBack,
                        // Named only where the bar has room to say it, which is
                        // also where a window has no other title of its own.
                        title = document?.info?.displayTitle(),
                        modifier = Modifier
                            // Held to the width of the page it acts on rather
                            // than to the window's. A capsule stretched across a
                            // monitor puts its three controls an inch from three
                            // different edges, and relates to nothing on screen.
                            .widthIn(max = dimens.maxReaderWidth)
                            .padding(
                                start = dimens.screenPadding,
                                end = dimens.screenPadding,
                                top = ChromeVerticalPadding,
                                bottom = ChromeVerticalPadding
                            ),
                        centerControl = {
                            // Only once there is a book to be somewhere in.
                            if (document != null) {
                                PageIndicatorButton(
                                    listState = listState,
                                    unitCount = document.content.unitCount,
                                    unit = document.content.unit,
                                    onClick = {
                                        settingsExpanded = false
                                        contentsExpanded = !contentsExpanded
                                    }
                                )
                            }
                        },
                        endControl = {
                            ShortcutTooltip(description = "Reading settings", shortcut = "T") {
                                ToolbarButton(
                                    onClick = {
                                        contentsExpanded = false
                                        settingsExpanded = !settingsExpanded
                                    },
                                    label = "Reading settings"
                                ) {
                                    TextSettingsGlyph(tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    )
                }

                // The panels are laid out in the reader's own window rather than
                // in popups of their own. A popup is a separate, non-focusable
                // window: keyboards and switch controls cannot reach into one,
                // and making it focusable instead means it swallows the press
                // that should have opened the other panel. In-window, both work.
                if (document != null) {
                    ReaderContentsPanel(
                        expanded = contentsExpanded,
                        listState = listState,
                        unitCount = document.content.unitCount,
                        unit = document.content.unit,
                        outline = outline,
                        onGoToPage = { pageIndex ->
                            scope.launch { listState.scrollToItem(pageIndex) }
                        },
                        onDismissRequest = { contentsExpanded = false },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = ChromeHeight)
                    )
                }

                ReaderSettingsPanel(
                    expanded = settingsExpanded,
                    settings = settings,
                    // A PDF's type was set when the file was made.
                    typographyEnabled = document?.content is ReflowableContent,
                    onSettingsChange = onSettingsChange,
                    onDismissRequest = { settingsExpanded = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = ChromeHeight, end = dimens.screenPadding)
                )
            }
        }
    }
}

/**
 * Carries out what the keyboard asked for.
 *
 * Deliberately built per composition rather than remembered: every one of these
 * closes over something that changes as the book is read - the settings, the
 * page count, which panel is open - and a lambda held across those changes would
 * act on the reader of a moment ago. It costs an allocation the page list would
 * not notice.
 */
@Composable
private fun readerCommands(
    listState: LazyListState,
    unitCount: Int,
    reflowable: Boolean,
    settings: ReaderSettings,
    onSettingsChange: (ReaderSettings) -> Unit,
    onBack: () -> Unit,
    onToggleContents: () -> Unit,
    onToggleSettings: () -> Unit
): (ReaderCommand) -> Unit {
    val scope = rememberCoroutineScope()

    return { command ->
        val lastPage = unitCount - 1
        val page = listState.firstVisibleItemIndex

        fun goTo(index: Int) {
            if (unitCount > 0) {
                scope.launch { listState.animateScrollToItem(index.coerceIn(0, lastPage)) }
            }
        }

        fun nudge(fraction: Float) {
            val viewport = listState.layoutInfo.viewportSize.height
            if (viewport > 0) scope.launch { listState.animateScrollBy(viewport * fraction) }
        }

        when (command) {
            ReaderCommand.NextPage -> goTo(page + 1)
            ReaderCommand.PreviousPage -> goTo(page - 1)
            ReaderCommand.FirstPage -> goTo(0)
            ReaderCommand.LastPage -> goTo(lastPage)

            ReaderCommand.ScrollDown -> nudge(SCROLL_NUDGE_FRACTION)
            ReaderCommand.ScrollUp -> nudge(-SCROLL_NUDGE_FRACTION)

            ReaderCommand.Wider -> onSettingsChange(
                if (reflowable) {
                    settings.withLargerText()
                } else {
                    settings.withPageScale(settings.pageScale + PAGE_SCALE_STEP)
                }
            )

            ReaderCommand.Narrower -> onSettingsChange(
                if (reflowable) {
                    settings.withSmallerText()
                } else {
                    settings.withPageScale(settings.pageScale - PAGE_SCALE_STEP)
                }
            )

            ReaderCommand.ActualSize -> onSettingsChange(
                if (reflowable) {
                    settings.withTextScale(ReaderSettings.DEFAULT_TEXT_SCALE)
                } else {
                    settings.withPageScale(ReaderSettings.DEFAULT_PAGE_SCALE)
                }
            )

            ReaderCommand.ToggleContents -> onToggleContents()
            ReaderCommand.ToggleSettings -> onToggleSettings()
            ReaderCommand.Close -> onBack()
        }
    }
}

@Composable
private fun PageList(
    pages: PagedContent,
    listState: LazyListState,
    pageScale: Float,
    pageColor: Color,
    pageFilter: ColorFilter?,
    onToggleChrome: () -> Unit,
    onPageScaleChange: (Float) -> Unit,
    renderPage: suspend (index: Int, widthPx: Int) -> ImageBitmap?
) {
    val dimens = LocalLukiaDimens.current
    val pageSizes = pages.pages.pageSizes

    // Held here while a pinch is in progress and committed when it ends. Writing
    // every intermediate scale into the settings would persist it to storage
    // sixty times a second, and the page follows the fingers just as closely
    // from here.
    var pinchScale by remember { mutableStateOf<Float?>(null) }
    val effectiveScale = pinchScale ?: pageScale

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // One measurement for the whole book: every page is drawn to the same
        // width, so working it out per page would be the same answer computed
        // hundreds of times.
        val columnWidth = if (dimens.maxReaderWidth == Dp.Unspecified) {
            maxWidth
        } else {
            minOf(maxWidth, dimens.maxReaderWidth)
        }
        val pageWidth = columnWidth * effectiveScale
        val renderWidthPx = with(LocalDensity.current) { renderWidthFor(pageWidth.roundToPx()) }

        // Zoomed in, the page is wider than the window and the extra width is
        // the point - so the book is laid out at its own width and panned,
        // rather than squeezed back into the space available.
        val contentWidth = maxOf(pageWidth, maxWidth)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pinchToZoom(
                    scale = effectiveScale,
                    range = ReaderSettings.MIN_PAGE_SCALE..ReaderSettings.MAX_PAGE_SCALE,
                    onPinch = { pinchScale = it },
                    onPinchEnd = {
                        pinchScale?.let(onPageScaleChange)
                        pinchScale = null
                    }
                )
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onToggleChrome() })
                }
                .horizontalScroll(rememberScrollState())
        ) {
            LazyColumn(
                state = listState,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(PageGap),
                contentPadding = PaddingValues(
                    // Clears the floating chrome, so the first page is not behind
                    // it when the book opens.
                    top = ChromeHeight,
                    bottom = PageListPadding
                ),
                modifier = Modifier
                    .width(contentWidth)
                    .fillMaxHeight()
            ) {
                items(count = pageSizes.size) { index ->
                    DocumentPage(
                        index = index,
                        pageCount = pageSizes.size,
                        aspectRatio = pageSizes[index].aspectRatio,
                        width = pageWidth,
                        renderWidthPx = renderWidthPx,
                        pageColor = pageColor,
                        pageFilter = pageFilter,
                        renderPage = renderPage
                    )
                }
            }
        }
    }
}

/**
 * A reflowable book, section by section.
 *
 * The same scrolling list of sheets a PDF gets, so moving between a PDF and an
 * EPUB is not moving between two different readers - but what a sheet holds is
 * text this app has set, so the sheet is as tall as its text needs and the
 * settings act on the words rather than on a picture of them.
 *
 * The measure never exceeds the window. Above 100% a PDF page is deliberately
 * drawn wider than the space available and panned, because that is the only way
 * to get closer to type whose size was fixed when the file was made; text has no
 * such problem, and a line of prose running off both edges of the window is not
 * a way to read anything. So the width presets set the measure up to the width
 * available and stop there, and getting closer is what the text size is for -
 * which is also what a pinch does here.
 */
@Composable
private fun SectionList(
    sections: List<TextSection>,
    listState: LazyListState,
    settings: ReaderSettings,
    pageColor: Color,
    onToggleChrome: () -> Unit,
    onSettingsChange: (ReaderSettings) -> Unit
) {
    val dimens = LocalLukiaDimens.current

    // Held here while a pinch is in progress and committed when it ends, so the
    // type follows the fingers without every intermediate size being written to
    // storage.
    var pinchScale by remember { mutableStateOf<Float?>(null) }
    val effectiveScale = pinchScale ?: settings.textScale

    // Built from the size the fingers are at rather than the size that has been
    // stored, which is what makes the whole page re-set as the pinch happens.
    val style = readerTextStyle(settings.withTextScale(effectiveScale))

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val columnWidth = if (dimens.maxReaderWidth == Dp.Unspecified) {
            maxWidth
        } else {
            minOf(maxWidth, dimens.maxReaderWidth)
        }

        val sheetWidth = minOf(columnWidth * settings.pageScale, maxWidth)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pinchToZoom(
                    scale = effectiveScale,
                    range = ReaderSettings.MIN_TEXT_SCALE..ReaderSettings.MAX_TEXT_SCALE,
                    onPinch = { pinchScale = it },
                    onPinchEnd = {
                        pinchScale?.let { onSettingsChange(settings.withTextScale(it)) }
                        pinchScale = null
                    }
                )
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onToggleChrome() })
                }
        ) {
            LazyColumn(
                state = listState,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(PageGap),
                contentPadding = PaddingValues(
                    top = ChromeHeight,
                    bottom = PageListPadding
                ),
                modifier = Modifier.fillMaxSize()
            ) {
                items(count = sections.size) { index ->
                    Box(
                        modifier = Modifier
                            .width(sheetWidth)
                            .shadow(elevation = 2.dp)
                            .background(pageColor)
                            .padding(SheetPadding)
                            // Unlike a rendered page there is real text behind
                            // this, so the description says where in the book it
                            // is and leaves the reading to the text itself.
                            .semantics {
                                contentDescription =
                                    "Section ${index + 1} of ${sections.size}"
                            }
                    ) {
                        TextSectionView(section = sections[index], style = style)
                    }
                }
            }
        }
    }
}

/**
 * One page. The sheet is laid out at the page's own proportions before anything
 * is drawn into it, so the list's height is settled from the start and scrolling
 * never jumps as pages arrive.
 *
 * [pageFilter] is what makes the reading theme instant: switching it re-draws
 * the page that is already rendered rather than rendering it again.
 */
@Composable
private fun DocumentPage(
    index: Int,
    pageCount: Int,
    aspectRatio: Float,
    width: Dp,
    renderWidthPx: Int,
    pageColor: Color,
    pageFilter: ColorFilter?,
    renderPage: suspend (index: Int, widthPx: Int) -> ImageBitmap?
) {
    // Keyed on the width as well as the page: a resized window, or a different
    // zoom, invalidates what was drawn for the old one.
    val page by produceState<ImageBitmap?>(initialValue = null, index, renderWidthPx) {
        value = renderPage(index, renderWidthPx)

        // Draw the next page while this one is being read, so arriving at it
        // does not mean waiting for it. Cancelled along with this page when it
        // scrolls away, which is what stops a fast scroll from queueing work for
        // pages already passed.
        renderPage(index + 1, renderWidthPx)
    }

    Box(
        modifier = Modifier
            .width(width)
            .aspectRatio(aspectRatio)
            .shadow(elevation = 2.dp)
            .background(pageColor)
            // A page is a picture with no text behind it, so this is the only
            // thing a screen reader can be told about it. Without it the whole
            // document is silence.
            .semantics { contentDescription = "Page ${index + 1} of $pageCount" }
    ) {
        page?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                // The sheet is already at the page's proportions, so this only
                // ever scales - it never crops or letterboxes.
                contentScale = ContentScale.FillBounds,
                colorFilter = pageFilter,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Pinch to zoom, without taking the drag that scrolls the book.
 *
 * Only claims the gesture once a second finger is down: one finger stays a
 * scroll, two become a zoom. A plain transform gesture would swallow both.
 *
 * A composable extension rather than a `composed` modifier: `composed` makes a
 * chain opaque to Compose's modifier comparison, so the whole chain it appears in
 * is rebuilt on every recomposition instead of being reused. The `rememberUpdated`
 * values below are what let the gesture keep a single `pointerInput` across a
 * changing scale, which is the thing that actually matters here.
 */
@Composable
private fun Modifier.pinchToZoom(
    scale: Float,
    range: ClosedFloatingPointRange<Float>,
    onPinch: (Float) -> Unit,
    onPinchEnd: () -> Unit
): Modifier {
    val currentScale by rememberUpdatedState(scale)
    val currentOnPinch by rememberUpdatedState(onPinch)
    val currentOnPinchEnd by rememberUpdatedState(onPinchEnd)

    return pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var pinched = false

            do {
                val event = awaitPointerEvent()

                if (event.changes.size > 1) {
                    val zoom = event.calculateZoom()

                    if (zoom != 1f) {
                        pinched = true
                        currentOnPinch((currentScale * zoom).coerceIn(range))
                        event.changes.forEach { it.consume() }
                    }
                }
            } while (event.changes.any { it.pressed })

            if (pinched) currentOnPinchEnd()
        }
    }
}

/** A transparent press catcher covering the reader. */
@Composable
private fun DismissLayer(onDismiss: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onDismiss
            )
    )
}
