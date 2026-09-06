package io.lukia.features.reader.presentation.components.text

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import io.lukia.features.reader.domain.FigureSize
import io.lukia.features.reader.presentation.components.passages.BlockActions
import io.lukia.features.reader.presentation.components.passages.NoBlockActions
import io.lukia.features.reader.presentation.pagination.PageBlock
import io.lukia.features.reader.presentation.pagination.TextPage
import io.lukia.features.reader.presentation.theme.BLOCK_SPACING
import io.lukia.features.reader.presentation.translation.BookTranslations
import io.lukia.features.reader.presentation.translation.TranslateOnScreen
import io.lukia.features.translate.domain.settings.TranslationGestures

/**
 * One page of a reflowable book, set at the reader's chosen size.
 *
 * Everything here is measured against [style] rather than given a size of its
 * own: a heading is the body size scaled up, the space between blocks is a
 * proportion of the leading. That is what makes the text size setting act on the
 * whole page instead of only on its paragraphs - a book at 200% with the same
 * gaps it had at 100% reads as text that has outgrown its own layout.
 *
 * What is on the page was chosen to fit it before any of this was drawn, by
 * measuring the same blocks against the same styles - see `BlockMetrics`. This
 * draws what it is given and does not re-decide anything.
 *
 * Each block is handed only its own slice of the translations. That is what
 * keeps translating one sentence from being work for the rest of the page: a
 * block whose slice compares equal to the one it was given last time is not
 * composed again at all.
 */
@Composable
internal fun TextPageView(
    page: TextPage,
    style: TextStyle,
    modifier: Modifier = Modifier,
    /** How tall the page is, so a plate can be drawn no taller than one. */
    pageHeight: Dp = Dp.Unspecified,
    /** How large illustrations are drawn, which is the reader's own setting. */
    figureSize: FigureSize = FigureSize.Normal,
    translations: BookTranslations = BookTranslations.None,
    actionsFor: (PageBlock) -> BlockActions = { NoBlockActions },
    translateOnScreen: TranslateOnScreen = TranslateOnScreen.Idle,
    gestures: TranslationGestures = TranslationGestures.Default,
    /** Whether the type is being resized right now - a pinch in progress. */
    resizingType: Boolean = false,
) {
    val spacing = with(LocalDensity.current) {
        (style.fontSize * BLOCK_SPACING).toDp()
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(spacing),
        modifier = modifier,
    ) {
        page.blocks.forEach { pageBlock ->
            TextBlockView(
                block = pageBlock.block,
                style = style,
                // Shifted by what ran onto the pages before this one, so a
                // sentence is the same sentence of the same paragraph wherever
                // the layout happened to put it.
                translations = translations
                    .section(pageBlock.sectionIndex)
                    .block(pageBlock.blockIndex)
                    .from(pageBlock.sentenceOffset),
                actions = actionsFor(pageBlock),
                translateOnScreen = translateOnScreen,
                gestures = gestures,
                resizingType = resizingType,
                maxHeight = pageHeight,
                figureSize = figureSize,
            )
        }
    }
}
