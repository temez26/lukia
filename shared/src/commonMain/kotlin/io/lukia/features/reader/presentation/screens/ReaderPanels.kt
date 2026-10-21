package io.lukia.features.reader.presentation.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.lukia.features.reader.domain.ReaderDocument
import io.lukia.features.reader.domain.ReaderSettings
import io.lukia.features.reader.domain.ReflowableContent
import io.lukia.features.reader.domain.outline.ReaderOutline
import io.lukia.features.reader.presentation.components.contents.ContentsActions
import io.lukia.features.reader.presentation.components.contents.ReaderContentsPanel
import io.lukia.features.reader.presentation.components.settings.ReaderSettingsPanel
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.isWideWindow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * What opens over the book: its contents, and how it reads.
 *
 * Laid out in the reader's own window rather than in popups of their own. A
 * popup is a separate, non-focusable window: keyboards and switch controls
 * cannot reach into one, and making it focusable instead means it swallows the
 * press that should have opened the other panel. In-window, both work.
 */
@Composable
internal fun BoxScope.ReaderPanels(
    chrome: ReaderChromeState,
    document: ReaderDocument?,
    /** How long the book is in pages, which is what the contents counts against. */
    pageCount: Int,
    /** False while a reflowable book is still being laid out and [pageCount] climbs. */
    pageCountFinal: Boolean,
    outline: ReaderOutline,
    listState: LazyListState,
    settings: ReaderSettings,
    layout: ReaderLayoutUi,
    onSettingsChange: (ReaderSettings) -> Unit,
    /** What a phone puts at the top of the settings panel; empty on a wider window. */
    translationSection: @Composable () -> Unit = {},
) {
    val dimens = LocalLukiaDimens.current
    val chromeHeight = chromeHeight()
    val scope = rememberCoroutineScope()

    // A phone's panels are as wide as its window, so they are inset from both
    // edges rather than hung off one of them - see ReaderSettingsPanel. A wider
    // window keeps them where the control that opened them is.
    val wide = isWideWindow
    val sidePadding = if (wide) 0.dp else dimens.screenPadding

    // A panel is a panel because it is inset from every edge, and on a tall
    // window it never grows far enough down for the bottom one to be tested.
    // On a phone in landscape it does: with nothing here, both of these ran
    // flush to the bottom of the safe area and stopped reading as something
    // floating over the book. The same gutter the chrome above them uses.
    val bottomPadding = ChromeVerticalPadding

    val goToChapter = rememberGoToChapter(document, outline, listState)

    if (document != null) {
        ReaderContentsPanel(
            expanded = chrome.contentsExpanded,
            listState = listState,
            pageCount = pageCount,
            pageCountFinal = pageCountFinal,
            outline = outline,
            book = document.info,
            actions = ContentsActions(
                onGoToPage = { pageIndex -> scope.launch { listState.scrollToItem(pageIndex) } },
                onGoToChapter = goToChapter,
                onDismiss = chrome::closePanels,
            ),
            barHeight = chrome.barHeight,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(
                    // A phone's contents open out of the bar, so they start where
                    // it does - see ReaderContentsPanel.
                    top = if (wide) chromeHeight else ChromeVerticalPadding,
                    bottom = bottomPadding,
                    start = sidePadding,
                    end = sidePadding,
                ),
        )
    }

    // Hung from the bar's end, under the button that opened it, on a window:
    // the bar is held to the page's width, so the window's own edge is nowhere
    // near that button. A phone's is centred, as wide as its window.
    Box(
        contentAlignment = if (wide) Alignment.TopEnd else Alignment.TopCenter,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .widthIn(max = dimens.maxReaderWidth)
            .fillMaxWidth(),
    ) {
        ReaderSettingsPanel(
            expanded = chrome.settingsExpanded,
            settings = settings,
            // A PDF's type was set when the file was made.
            typographyEnabled = document?.content is ReflowableContent,
            // Only a PDF can be read either way, and which book this is comes from
            // the choice rather than from what is open: converting one takes long
            // enough that there is nothing open for most of it, and that is when
            // somebody who has changed their mind goes looking for this control.
            layout = layout.layout.takeIf { layout.offered },
            onSettingsChange = onSettingsChange,
            onLayoutChange = layout.onLayoutChange,
            onDismissRequest = chrome::closePanels,
            modifier = Modifier.padding(
                top = chromeHeight,
                bottom = bottomPadding,
                start = sidePadding,
                end = dimens.screenPadding,
            ),
            translationSection = translationSection,
        )
    }
}

/**
 * Goes to an entry of [outline] by its index, now or once the layout reaches it.
 *
 * A chapter chosen before the layout got there used to be sent to the last page
 * laid out so far, so the reader landed somewhere the contents never said and
 * had to go back to the panel to try again.
 */
@Composable
private fun rememberGoToChapter(
    document: ReaderDocument?,
    outline: ReaderOutline,
    listState: LazyListState,
): (entryIndex: Int) -> Unit {
    var awaited by remember(document) { mutableStateOf<Int?>(null) }
    val entries = (outline as? ReaderOutline.Chapters)?.entries

    LaunchedEffect(awaited, entries) {
        val entry = awaited?.let { entries?.getOrNull(it) } ?: return@LaunchedEffect

        if (entry.placed) {
            awaited = null
            listState.scrollToItem(entry.pageIndex)
        }
    }

    // Turning the page by hand while waiting is a change of mind.
    LaunchedEffect(awaited) {
        if (awaited == null) return@LaunchedEffect
        snapshotFlow { listState.isScrollInProgress }.first { it }
        awaited = null
    }

    return { index -> awaited = index }
}
