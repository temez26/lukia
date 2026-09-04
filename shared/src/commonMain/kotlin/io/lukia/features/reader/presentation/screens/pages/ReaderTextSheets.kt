package io.lukia.features.reader.presentation.screens.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import io.lukia.features.reader.domain.FigureSize
import io.lukia.features.reader.domain.ReaderSettings
import io.lukia.features.reader.presentation.components.passages.BlockActions
import io.lukia.features.reader.presentation.components.text.TextPageView
import io.lukia.features.reader.presentation.pagination.PageBlock
import io.lukia.features.reader.presentation.pagination.PaginatedBook
import io.lukia.features.reader.presentation.pagination.TextPage
import io.lukia.features.reader.presentation.screens.SheetPadding
import io.lukia.features.reader.presentation.screens.chromeHeight
import io.lukia.features.reader.presentation.translation.TranslateOnScreen
import io.lukia.features.translate.domain.settings.TranslationGestures
import io.lukia.features.translate.presentation.BookTranslations
import io.lukia.resources.Res
import io.lukia.resources.reader_page_x_of_y
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.LukiaElevation
import org.jetbrains.compose.resources.stringResource

/**
 * The paper a reflowable book is set on, and the gestures that go with it.
 *
 * Split from [TextPageList], which owns the layout and the reader's place in it:
 * this is what a page of the book looks like, and it is the same answer for
 * every page in the book.
 */

/**
 * The two gestures the sheet does not answer for itself: a pinch that sets the
 * type, and a double tap that puts the chrome away.
 *
 * On the space beside the sheet as much as on the sheet, so there is nothing
 * here to point at - but the chrome answers to two taps wherever it is reached
 * from, so that it stays one gesture the reader learns once.
 */
@Composable
internal fun Modifier.typeGestures(
    scale: Float,
    onPinch: (Float) -> Unit,
    onPinchEnd: () -> Unit,
    onToggleChrome: () -> Unit,
): Modifier = this
    .pinchToZoom(
        scale = scale,
        range = ReaderSettings.MIN_TEXT_SCALE..ReaderSettings.MAX_TEXT_SCALE,
        onPinch = onPinch,
        onPinchEnd = onPinchEnd,
    )
    .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onToggleChrome() }) }

/**
 * How a sheet of this book is drawn: its size, its paper, and the type on it.
 *
 * Gathered rather than passed one at a time, because these five are one answer
 * to "what does a page of this book look like right now" and every sheet in the
 * book is drawn from exactly the same one.
 */
internal data class SheetStyle(
    val geometry: PageGeometry,
    val pageColor: Color,
    val textStyle: TextStyle,
    val figureSize: FigureSize,
    /** True while a pinch is in progress, so the type is running ahead of the layout. */
    val resizingType: Boolean,
)

/**
 * The book itself: a column of sheets, one for each page it has been laid out
 * into so far.
 *
 * How many that is moves while a long book is still being measured - see
 * [rememberPagination] - and this needs to know nothing about that. A page that
 * arrives is a page the list did not have, which is the one thing a lazy list is
 * already good at.
 */
@Composable
internal fun BookSheets(
    book: PaginatedBook,
    listState: LazyListState,
    sheet: SheetStyle,
    translations: BookTranslations,
    translateOnScreen: TranslateOnScreen,
    gestures: TranslationGestures,
    actionsFor: @Composable (TextPage) -> (PageBlock) -> BlockActions,
) {
    LazyColumn(
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PageGap),
        contentPadding = PaddingValues(top = chromeHeight(), bottom = PageListPadding),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(count = book.pageCount) { index ->
            val page = book.pages[index]

            PageSheet(
                number = index + 1,
                of = book.pageCount,
                size = DpSize(sheet.geometry.sheetWidth, sheet.geometry.pageHeight),
                color = sheet.pageColor,
            ) { textHeight ->
                TextPageView(
                    page = page,
                    style = sheet.textStyle,
                    pageHeight = textHeight,
                    figureSize = sheet.figureSize,
                    translations = translations,
                    translateOnScreen = translateOnScreen,
                    gestures = gestures,
                    resizingType = sheet.resizingType,
                    actionsFor = actionsFor(page),
                )
            }
        }
    }
}

/**
 * How big a sheet is in this window, and how much of it is text.
 *
 * One measurement for the whole book: every page is drawn to the same size, so
 * working it out per page would be the same answer computed hundreds of times -
 * and the book was laid out against exactly these numbers.
 */
internal data class PageGeometry(
    val sheetWidth: Dp,
    val pageHeight: Dp,
    val textWidth: Dp,
    val textHeight: Dp,
)

/**
 * The window with the chrome and the list's own padding taken out of it, and
 * then the sheet's own margins.
 */
@Composable
internal fun BoxWithConstraintsScope.pageGeometry(pageScale: Float): PageGeometry {
    val dimens = LocalLukiaDimens.current

    val columnWidth = if (dimens.maxReaderWidth == Dp.Unspecified) {
        maxWidth
    } else {
        minOf(maxWidth, dimens.maxReaderWidth)
    }

    val sheetWidth = minOf(columnWidth * pageScale, maxWidth)
    val pageHeight = maxHeight - chromeHeight() - PageListPadding

    return PageGeometry(
        sheetWidth = sheetWidth,
        pageHeight = pageHeight,
        textWidth = sheetWidth - SheetPadding * 2,
        textHeight = pageHeight - SheetPadding * 2,
    )
}

/**
 * One sheet of the book: a page's worth of paper, and what is set on it.
 *
 * Given the height rather than taking the height its text needs, unlike the
 * blocks inside it. A page whose paper stopped where its words did would be a
 * scrolling list of ragged cards, and the number in the toolbar would be
 * counting something the reader cannot see the edges of.
 */
@Composable
private fun PageSheet(
    number: Int,
    of: Int,
    size: DpSize,
    color: Color,
    content: @Composable (textHeight: Dp) -> Unit,
) {
    // Read before the modifier chain: `semantics` is not a composable scope.
    val spoken = stringResource(Res.string.reader_page_x_of_y, number, of)

    Box(
        modifier = Modifier
            .width(size.width)
            .height(size.height)
            .shadow(elevation = LukiaElevation.page)
            .background(color)
            // Text set larger than the page was cut for - which is what a pinch
            // in progress is - stops at the edge of the sheet rather than
            // running over the one below.
            .clipToBounds()
            .padding(SheetPadding)
            // One group, so the number below is ordered against the page's own
            // blocks rather than against every page in the list.
            .semantics { isTraversalGroup = true },
    ) {
        PageLabel(spoken)

        content(size.height - SheetPadding * 2)
    }
}
