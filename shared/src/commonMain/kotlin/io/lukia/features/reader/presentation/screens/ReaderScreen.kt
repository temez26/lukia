package io.lukia.features.reader.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.core.domain.DocumentType
import io.lukia.core.presentation.UiState
import io.lukia.core.presentation.dataOrNull
import io.lukia.features.reader.domain.ReaderDocument
import io.lukia.features.reader.domain.ReaderOutline
import io.lukia.features.reader.domain.ReaderSettings
import io.lukia.features.reader.presentation.ReaderViewModel
import io.lukia.features.reader.presentation.components.PageIndicatorButton
import io.lukia.features.reader.presentation.components.ReaderContentsPanel
import io.lukia.features.reader.presentation.components.ReaderSettingsPanel
import io.lukia.features.reader.presentation.components.ReaderToolbar
import io.lukia.features.reader.presentation.components.TextSettingsGlyph
import io.lukia.features.reader.presentation.components.ToolbarButton
import io.lukia.features.reader.presentation.renderWidthFor
import io.lukia.features.reader.presentation.theme.ReaderTheming
import io.lukia.features.reader.presentation.theme.readerPageStyle
import io.lukia.shared.presentation.components.UiStateContent
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.topChromeIn
import io.lukia.shared.presentation.theme.topChromeOut
import kotlinx.coroutines.flow.filter
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

@Composable
fun ReaderScreen(
    location: String,
    onBack: () -> Unit,
    viewModel: ReaderViewModel = koinViewModel(parameters = { parametersOf(location) })
) {
    val uiState by viewModel.uiState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val outline by viewModel.outline.collectAsState()

    ReaderContent(
        uiState = uiState,
        settings = settings,
        outline = outline,
        onBack = onBack,
        onRetry = viewModel::retry,
        onSettingsChange = viewModel::updateSettings,
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
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSettingsChange: (ReaderSettings) -> Unit,
    renderPage: suspend (index: Int, widthPx: Int) -> ImageBitmap?
) {
    val dimens = LocalLukiaDimens.current
    val scope = rememberCoroutineScope()
    var settingsExpanded by remember { mutableStateOf(false) }
    var contentsExpanded by remember { mutableStateOf(false) }
    var chromeVisible by remember { mutableStateOf(true) }

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

        // Reading puts the chrome away; a tap brings it back. Driven by a single
        // boolean rather than by the scroll position, so this costs nothing per
        // scrolled pixel.
        LaunchedEffect(listState) {
            snapshotFlow { listState.isScrollInProgress }
                .filter { it }
                .collect { chromeVisible = false }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
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
                    PageList(
                        document = openDocument,
                        listState = listState,
                        pageScale = settings.pageScale,
                        pageColor = pageStyle.pageColor,
                        pageFilter = pageStyle.pageFilter,
                        onToggleChrome = { chromeVisible = !chromeVisible },
                        onPageScaleChange = { onSettingsChange(settings.withPageScale(it)) },
                        renderPage = renderPage
                    )
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
                        modifier = Modifier.padding(
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
                                    pageCount = document.pages.pageCount,
                                    onClick = {
                                        settingsExpanded = false
                                        contentsExpanded = !contentsExpanded
                                    }
                                )
                            }
                        },
                        endControl = {
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
                        pageCount = document.pages.pageCount,
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
                    typographyEnabled = document != null &&
                        document.info.type != DocumentType.PDF,
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

@Composable
private fun PageList(
    document: ReaderDocument,
    listState: LazyListState,
    pageScale: Float,
    pageColor: Color,
    pageFilter: ColorFilter?,
    onToggleChrome: () -> Unit,
    onPageScaleChange: (Float) -> Unit,
    renderPage: suspend (index: Int, widthPx: Int) -> ImageBitmap?
) {
    val dimens = LocalLukiaDimens.current
    val pageSizes = document.pages.pageSizes

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
                        currentOnPinch(
                            (currentScale * zoom).coerceIn(
                                ReaderSettings.MIN_PAGE_SCALE,
                                ReaderSettings.MAX_PAGE_SCALE
                            )
                        )
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
