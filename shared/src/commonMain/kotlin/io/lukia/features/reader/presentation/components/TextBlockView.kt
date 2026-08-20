package io.lukia.features.reader.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import io.lukia.features.reader.domain.FigureSize
import io.lukia.features.reader.domain.TextBlock
import io.lukia.features.reader.presentation.TranslateOnScreen
import io.lukia.features.translate.domain.TranslationGestures
import io.lukia.features.translate.presentation.SentenceTranslations

/** One thing on the page, drawn as whichever kind of block it is. */
@Composable
internal fun TextBlockView(
    block: TextBlock,
    style: TextStyle,
    translations: SentenceTranslations,
    actions: BlockActions,
    translateOnScreen: TranslateOnScreen,
    gestures: TranslationGestures,
    resizingType: Boolean,
    /** How tall a figure may be drawn, which on a paginated book is its page. */
    maxHeight: Dp = Dp.Unspecified,
    /** How large illustrations are drawn, which is the reader's own setting. */
    figureSize: FigureSize = FigureSize.Normal,
) {
    when (block) {
        is TextBlock.Heading ->
            HeadingBlock(block, style, translations, actions, translateOnScreen, gestures, resizingType)

        is TextBlock.Paragraph -> ProseText(
            runs = block.text,
            style = style,
            translations = translations,
            actions = actions,
            translateOnScreen = translateOnScreen,
            gestures = gestures,
            resizingType = resizingType,
        )

        is TextBlock.ListItem ->
            ListItemBlock(block, style, translations, actions, translateOnScreen, gestures, resizingType)

        is TextBlock.Quote ->
            QuoteBlock(block, style, translations, actions, translateOnScreen, gestures, resizingType)
        // Preformatted text is not prose. Splitting code at its full stops would
        // offer to translate `object.method()` as a sentence.
        is TextBlock.Code -> CodeBlock(block, style)
        is TextBlock.Figure -> FigureBlock(block, maxHeight, figureSize)
        TextBlock.Separator -> SeparatorBlock()
    }
}
