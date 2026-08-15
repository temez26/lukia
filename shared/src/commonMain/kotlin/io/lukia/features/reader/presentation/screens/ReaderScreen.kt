package io.lukia.features.reader.presentation.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.core.presentation.UiState
import io.lukia.features.reader.domain.ReaderDocument
import io.lukia.features.reader.presentation.ReaderViewModel
import io.lukia.shared.presentation.components.BackChevron
import io.lukia.shared.presentation.components.GlassSurface
import io.lukia.shared.presentation.components.UiStateContent
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Vertical room reserved so the first page starts clear of the back control. */
private val TopChromeClearance: Dp = 76.dp

/** Room at the end of the book so the last page clears the page indicator. */
private val BottomChromeClearance: Dp = 88.dp

/** Gap between pages, which is what makes them read as separate sheets. */
private val PageGap: Dp = 12.dp

/** How pages are drawn: what a page renderer paints on. */
private val PageColor = Color.White

@Composable
fun ReaderScreen(
    location: String,
    onBack: () -> Unit,
    viewModel: ReaderViewModel = koinViewModel(parameters = { parametersOf(location) })
) {
    val uiState by viewModel.uiState.collectAsState()

    ReaderContent(
        uiState = uiState,
        onBack = onBack,
        onRetry = viewModel::retry,
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
    onBack: () -> Unit,
    onRetry: () -> Unit,
    renderPage: suspend (index: Int, widthPx: Int) -> ImageBitmap?
) {
    val dimens = LocalLukiaDimens.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Full-bleed like the detail screen: pages pass under the floating
        // controls, so insets are applied to the chrome by hand rather than by a
        // Scaffold. The background above stays outside the inset so colour still
        // reaches the screen edges.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            UiStateContent(
                state = uiState,
                loadingMessage = "Opening…",
                // Opening can fail on something worth a second attempt - a copy
                // that could not be written, a file being read as it is moved.
                actionLabel = "Try again",
                onAction = onRetry,
                modifier = Modifier.fillMaxSize()
            ) { document ->
                PageList(
                    document = document,
                    renderPage = renderPage
                )
            }

            GlassSurface(
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = dimens.screenPadding, top = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable(onClickLabel = "Close book", onClick = onBack)
                        .size(44.dp),
                    contentAlignment = Alignment.Center
                ) {
                    BackChevron(
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PageList(
    document: ReaderDocument,
    renderPage: suspend (index: Int, widthPx: Int) -> ImageBitmap?
) {
    val dimens = LocalLukiaDimens.current
    val listState = rememberLazyListState()
    val pageSizes = document.pages.pageSizes

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // One measurement for the whole book: every page is drawn to the same
        // width, so working it out per page would be the same answer computed
        // hundreds of times.
        val pageWidth = if (dimens.maxReaderWidth == Dp.Unspecified) {
            maxWidth
        } else {
            minOf(maxWidth, dimens.maxReaderWidth)
        }
        val pageWidthPx = with(LocalDensity.current) { pageWidth.roundToPx() }

        LazyColumn(
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PageGap),
            contentPadding = PaddingValues(
                top = TopChromeClearance,
                bottom = BottomChromeClearance
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            items(count = pageSizes.size) { index ->
                DocumentPage(
                    index = index,
                    aspectRatio = pageSizes[index].aspectRatio,
                    width = pageWidth,
                    widthPx = pageWidthPx,
                    renderPage = renderPage
                )
            }
        }

        PageIndicator(
            listState = listState,
            pageCount = pageSizes.size,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        )
    }
}

/**
 * One page. The sheet is laid out at the page's own proportions before anything
 * is drawn into it, so the list's height is settled from the start and scrolling
 * never jumps as pages arrive.
 */
@Composable
private fun DocumentPage(
    index: Int,
    aspectRatio: Float,
    width: Dp,
    widthPx: Int,
    renderPage: suspend (index: Int, widthPx: Int) -> ImageBitmap?
) {
    // Keyed on the width as well as the page: a resized window invalidates what
    // was drawn for the old one.
    val page by produceState<ImageBitmap?>(initialValue = null, index, widthPx) {
        value = renderPage(index, widthPx)
    }

    Box(
        modifier = Modifier
            .width(width)
            .aspectRatio(aspectRatio)
            .shadow(elevation = 2.dp)
            .background(PageColor)
    ) {
        page?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                // The sheet is already at the page's proportions, so this only
                // ever scales - it never crops or letterboxes.
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Where the reader is in the book. Placed over the page rather than in a bar so
 * nothing is taken away from the page itself.
 */
@Composable
private fun PageIndicator(
    listState: LazyListState,
    pageCount: Int,
    modifier: Modifier = Modifier
) {
    // derivedStateOf so this re-reads only when the page changes, not on every
    // pixel of scroll.
    val currentPage by remember(listState) {
        derivedStateOf { listState.firstVisibleItemIndex + 1 }
    }

    GlassSurface(shape = CircleShape, modifier = modifier) {
        Text(
            text = "$currentPage of $pageCount",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}
