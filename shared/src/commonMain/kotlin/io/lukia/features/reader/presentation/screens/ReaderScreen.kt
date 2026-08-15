package io.lukia.features.reader.presentation.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.core.domain.DocumentType
import io.lukia.core.presentation.UiState
import io.lukia.core.presentation.dataOrNull
import io.lukia.features.reader.domain.ReaderDocument
import io.lukia.features.reader.domain.ReaderSettings
import io.lukia.features.reader.presentation.ReaderViewModel
import io.lukia.features.reader.presentation.components.ReaderSettingsPanel
import io.lukia.features.reader.presentation.components.ReaderToolbar
import io.lukia.features.reader.presentation.components.TextSettingsGlyph
import io.lukia.features.reader.presentation.components.ToolbarButton
import io.lukia.features.reader.presentation.theme.ReaderTheming
import io.lukia.features.reader.presentation.theme.readerPageStyle
import io.lukia.shared.presentation.components.UiStateContent
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Gap between pages, which is what makes them read as separate sheets. */
private val PageGap: Dp = 12.dp

/** Breathing room at either end of the book. */
private val PageListPadding: Dp = 8.dp

@Composable
fun ReaderScreen(
    location: String,
    onBack: () -> Unit,
    viewModel: ReaderViewModel = koinViewModel(parameters = { parametersOf(location) })
) {
    val uiState by viewModel.uiState.collectAsState()
    val settings by viewModel.settings.collectAsState()

    ReaderContent(
        uiState = uiState,
        settings = settings,
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
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSettingsChange: (ReaderSettings) -> Unit,
    renderPage: suspend (index: Int, widthPx: Int) -> ImageBitmap?
) {
    val dimens = LocalLukiaDimens.current
    var settingsExpanded by remember { mutableStateOf(false) }

    // The reader wears its own theme; the rest of the app keeps following the
    // system. Everything inside - the glass chrome, the type, the panel - picks
    // the reading colours up from the Material scheme without being passed them.
    ReaderTheming(theme = settings.theme) {
        val document = uiState.dataOrNull
        val pageStyle = readerPageStyle(settings.theme)
        // Created here rather than in the page list so that it survives the list
        // being replaced, and so the toolbar can read the page number from it.
        val listState = rememberLazyListState()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                ReaderToolbar(
                    listState = listState,
                    pageCount = document?.pages?.pageCount ?: 0,
                    onBack = onBack,
                    modifier = Modifier.padding(
                        start = dimens.screenPadding,
                        end = dimens.screenPadding,
                        top = 8.dp,
                        bottom = 8.dp
                    )
                ) {
                    Box {
                        ToolbarButton(
                            onClick = { settingsExpanded = !settingsExpanded },
                            label = "Reading settings"
                        ) {
                            TextSettingsGlyph(tint = MaterialTheme.colorScheme.primary)
                        }

                        ReaderSettingsPanel(
                            expanded = settingsExpanded,
                            settings = settings,
                            // A PDF's type was set when the file was made.
                            typographyEnabled = document != null &&
                                document.info.type != DocumentType.PDF,
                            onSettingsChange = onSettingsChange,
                            onDismissRequest = { settingsExpanded = false }
                        )
                    }
                }

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
                        readingWidth = settings.readingWidth.fraction,
                        pageColor = pageStyle.pageColor,
                        pageFilter = pageStyle.pageFilter,
                        renderPage = renderPage
                    )
                }
            }

            // Catches the press that dismisses the settings panel, which is a
            // non-focusable popup and cannot see it. Deliberately not dimmed:
            // the point of the panel is watching the page change as it is used.
            if (settingsExpanded) {
                DismissLayer(onDismiss = { settingsExpanded = false })
            }
        }
    }
}

@Composable
private fun PageList(
    document: ReaderDocument,
    listState: LazyListState,
    readingWidth: Float,
    pageColor: Color,
    pageFilter: ColorFilter?,
    renderPage: suspend (index: Int, widthPx: Int) -> ImageBitmap?
) {
    val dimens = LocalLukiaDimens.current
    val pageSizes = document.pages.pageSizes

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // One measurement for the whole book: every page is drawn to the same
        // width, so working it out per page would be the same answer computed
        // hundreds of times.
        val columnWidth = if (dimens.maxReaderWidth == Dp.Unspecified) {
            maxWidth
        } else {
            minOf(maxWidth, dimens.maxReaderWidth)
        }
        val pageWidth = columnWidth * readingWidth
        val pageWidthPx = with(LocalDensity.current) { pageWidth.roundToPx() }

        LazyColumn(
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PageGap),
            contentPadding = PaddingValues(
                top = PageListPadding,
                bottom = PageListPadding
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            items(count = pageSizes.size) { index ->
                DocumentPage(
                    index = index,
                    aspectRatio = pageSizes[index].aspectRatio,
                    width = pageWidth,
                    widthPx = pageWidthPx,
                    pageColor = pageColor,
                    pageFilter = pageFilter,
                    renderPage = renderPage
                )
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
    aspectRatio: Float,
    width: Dp,
    widthPx: Int,
    pageColor: Color,
    pageFilter: ColorFilter?,
    renderPage: suspend (index: Int, widthPx: Int) -> ImageBitmap?
) {
    // Keyed on the width as well as the page: a resized window, or a narrower
    // measure, invalidates what was drawn for the old one.
    val page by produceState<ImageBitmap?>(initialValue = null, index, widthPx) {
        value = renderPage(index, widthPx)
    }

    Box(
        modifier = Modifier
            .width(width)
            .aspectRatio(aspectRatio)
            .shadow(elevation = 2.dp)
            .background(pageColor)
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
