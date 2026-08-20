package io.lukia.features.reader.presentation.screens

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import io.lukia.features.reader.domain.ReaderSettings
import io.lukia.features.reader.presentation.PAGE_SCALE_STEP
import io.lukia.features.reader.presentation.ReaderCommand
import io.lukia.features.reader.presentation.SCROLL_NUDGE_FRACTION
import kotlinx.coroutines.launch

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
internal fun readerCommands(
    listState: LazyListState,
    pageCount: Int,
    reflowable: Boolean,
    settings: ReaderSettings,
    onSettingsChange: (ReaderSettings) -> Unit,
    onBack: () -> Unit,
    onToggleContents: () -> Unit,
    onToggleSettings: () -> Unit,
    onTranslateOnScreen: () -> Unit,
    onToggleContinuousTranslation: () -> Unit,
    onToggleTranslationsShown: () -> Unit,
): (ReaderCommand) -> Unit {
    val scope = rememberCoroutineScope()

    return { command ->
        val lastPage = pageCount - 1
        val page = listState.firstVisibleItemIndex

        fun goTo(index: Int) {
            if (pageCount > 0) {
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
                },
            )

            ReaderCommand.Narrower -> onSettingsChange(
                if (reflowable) {
                    settings.withSmallerText()
                } else {
                    settings.withPageScale(settings.pageScale - PAGE_SCALE_STEP)
                },
            )

            ReaderCommand.ActualSize -> onSettingsChange(
                if (reflowable) {
                    settings.withTextScale(ReaderSettings.DEFAULT_TEXT_SCALE)
                } else {
                    settings.withPageScale(ReaderSettings.DEFAULT_PAGE_SCALE)
                },
            )

            ReaderCommand.ToggleContents -> onToggleContents()
            ReaderCommand.ToggleSettings -> onToggleSettings()

            ReaderCommand.TranslatePage -> onTranslateOnScreen()
            ReaderCommand.ToggleContinuousTranslation -> onToggleContinuousTranslation()
            ReaderCommand.ToggleTranslationsShown -> onToggleTranslationsShown()

            ReaderCommand.Close -> onBack()
        }
    }
}
