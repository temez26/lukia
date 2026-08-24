package io.lukia.features.reader.presentation.screens

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.lukia.features.reader.domain.ReaderDocument
import io.lukia.features.reader.domain.ReaderOutline
import io.lukia.features.reader.domain.ReaderSettings
import io.lukia.features.reader.domain.ReflowableContent
import io.lukia.features.reader.presentation.components.ReaderContentsPanel
import io.lukia.features.reader.presentation.components.ReaderSettingsPanel
import io.lukia.shared.presentation.theme.LocalLukiaDimens
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
    outline: ReaderOutline,
    listState: LazyListState,
    settings: ReaderSettings,
    layout: ReaderLayoutUi,
    onSettingsChange: (ReaderSettings) -> Unit,
) {
    val dimens = LocalLukiaDimens.current
    val chromeHeight = chromeHeight()
    val scope = rememberCoroutineScope()

    if (document != null) {
        ReaderContentsPanel(
            expanded = chrome.contentsExpanded,
            listState = listState,
            pageCount = pageCount,
            outline = outline,
            onGoToPage = { pageIndex -> scope.launch { listState.scrollToItem(pageIndex) } },
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
        // Only a PDF can be read either way, and which book this is comes from
        // the choice rather than from what is open: converting one takes long
        // enough that there is nothing open for most of it, and that is when
        // somebody who has changed their mind goes looking for this control.
        layout = layout.layout.takeIf { layout.offered },
        onSettingsChange = onSettingsChange,
        onLayoutChange = layout.onLayoutChange,
        onDismissRequest = chrome::closePanels,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(top = chromeHeight, end = dimens.screenPadding),
    )
}
