package io.lukia.features.reader.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.lukia.features.reader.presentation.components.chrome.ReaderReadingStatus
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.bottomChromeIn
import io.lukia.shared.presentation.theme.bottomChromeOut

/**
 * The bar under the book: how far through it this page is.
 *
 * The other half of [ReaderTopChrome], and written as its mirror on purpose -
 * same measure, same inset, same rules about when it is there. The toolbar says
 * where the reader is and this says how much is left, and a reader who put the
 * chrome away asked for the page rather than for one of the two.
 *
 * Shown with the toolbar and only with it. A line and a figure left burning along
 * the bottom of a page somebody has just cleared are two things still asking to
 * be looked at.
 *
 * Floats over the page rather than taking a row of its own, unlike the toolbar
 * above - which the pages leave room for. Reserving a row here as well would cost
 * every page of the book a band of height for chrome that is away for most of the
 * reading, and would re-lay the whole book out to do it.
 */
@Composable
internal fun ReaderBottomChrome(
    chrome: ReaderChromeState,
    /** How long the book is; zero while it is still being laid out. */
    pageCount: Int,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalLukiaDimens.current

    AnimatedVisibility(
        visible = chrome.chromeShown && pageCount > 0,
        // Up from the bottom edge as the toolbar comes down the top one, so the
        // two read as one piece of chrome opening rather than as two things that
        // happen to appear at once.
        enter = bottomChromeIn(),
        exit = bottomChromeOut(),
        modifier = modifier,
    ) {
        ReaderReadingStatus(
            listState = listState,
            pageCount = pageCount,
            modifier = Modifier
                // Held to the width of the page it reports on rather than to the
                // window's, for the reason the toolbar is - a bar stretched
                // across a monitor relates to nothing on screen.
                .widthIn(max = dimens.maxReaderWidth)
                .padding(
                    start = dimens.screenPadding,
                    end = dimens.screenPadding,
                    top = ChromeVerticalPadding,
                    bottom = ChromeVerticalPadding,
                ),
        )
    }
}
