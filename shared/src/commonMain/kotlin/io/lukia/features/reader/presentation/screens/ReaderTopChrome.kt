package io.lukia.features.reader.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.lukia.core.presentation.displayTitle
import io.lukia.features.reader.domain.ReaderDocument
import io.lukia.features.reader.domain.outline.ReaderOutline
import io.lukia.features.reader.presentation.components.chrome.PageIndicatorButton
import io.lukia.features.reader.presentation.components.chrome.ReaderToolbar
import io.lukia.features.reader.presentation.components.settings.TextSettingsGlyph
import io.lukia.features.reader.presentation.screens.translation.ReaderTranslationUi
import io.lukia.features.reader.presentation.screens.translation.TranslationControls
import io.lukia.features.reader.presentation.screens.translation.inToolbar
import io.lukia.resources.Res
import io.lukia.resources.reader_reading_settings
import io.lukia.shared.presentation.components.controls.LukiaIconButton
import io.lukia.shared.presentation.components.controls.ShortcutTooltip
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.topChromeIn
import io.lukia.shared.presentation.theme.topChromeOut
import org.jetbrains.compose.resources.stringResource

/**
 * The bar over the book: back, where you are, and what can be done from here.
 *
 * Floats over the page rather than taking a row of its own, so that putting it
 * away gives the book the whole screen without re-laying out a single page.
 */
@Composable
internal fun ReaderTopChrome(
    chrome: ReaderChromeState,
    document: ReaderDocument?,
    /** How long the book is in pages, which for a reflowable one is what it was laid out into. */
    pageCount: Int,
    /** The chapters, already pointing at pages, so the bar can name the one in view. */
    outline: ReaderOutline,
    listState: LazyListState,
    translation: ReaderTranslationUi,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalLukiaDimens.current

    AnimatedVisibility(
        visible = chrome.chromeShown,
        enter = topChromeIn(),
        exit = topChromeOut(),
        modifier = modifier,
    ) {
        ReaderToolbar(
            onBack = onBack,
            // Named only where the bar has room to say it, which is also where a
            // window has no other title of its own.
            title = document?.info?.displayTitle(),
            modifier = Modifier
                // Held to the width of the page it acts on rather than to the
                // window's. A capsule stretched across a monitor puts its three
                // controls an inch from three different edges, and relates to
                // nothing on screen.
                .widthIn(max = dimens.maxReaderWidth)
                .padding(
                    start = dimens.screenPadding,
                    end = dimens.screenPadding,
                    top = ChromeVerticalPadding,
                    bottom = ChromeVerticalPadding,
                ),
            centerControl = {
                // Only once there is a book to be somewhere in.
                if (document != null && pageCount > 0) {
                    PageIndicatorButton(
                        listState = listState,
                        pageCount = pageCount,
                        chapters = (outline as? ReaderOutline.Chapters)?.entries.orEmpty(),
                        onClick = chrome::toggleContents,
                    )
                }
            },
            endControl = {
                // One child rather than several, because on a narrow window the
                // bar spaces its children evenly - and three controls drifting
                // apart across half a phone is not a group of related buttons.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // A phone's bar has no room for three more glyphs, so there
                    // they are rows in the settings panel instead - which is
                    // also where there is room to say what each one does.
                    if (translation.inToolbar) {
                        TranslationControls(
                            continuous = translation.onScreen.continuous,
                            shown = translation.shown,
                            offered = translation.offered,
                            onTranslateOnScreen = translation.onTranslateOnScreen,
                            onContinuousChange = translation.onContinuousChange,
                            onVisibleChange = translation.onVisibleChange,
                        )
                    }

                    ShortcutTooltip(
                        description = stringResource(Res.string.reader_reading_settings),
                        shortcut = "T",
                    ) {
                        LukiaIconButton(
                            label = stringResource(Res.string.reader_reading_settings),
                            onClick = chrome::toggleSettings,
                        ) { tint, glyph ->
                            TextSettingsGlyph(tint = tint, modifier = glyph)
                        }
                    }
                }
            },
        )
    }
}
