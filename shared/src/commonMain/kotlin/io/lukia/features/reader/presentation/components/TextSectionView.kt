package io.lukia.features.reader.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import io.lukia.features.reader.domain.TextBlock
import io.lukia.features.reader.domain.TextSection
import io.lukia.features.reader.presentation.TranslateOnScreen
import io.lukia.features.translate.domain.Sentence
import io.lukia.features.translate.domain.TranslationGestures
import io.lukia.features.translate.domain.Word
import io.lukia.features.translate.presentation.SectionTranslations
import io.lukia.features.translate.presentation.SentenceTranslations

/** The space between one block and the next, as a multiple of the text size. */
private const val BLOCK_SPACING = 0.7f

/**
 * What a reader can ask of the passages in one section.
 *
 * Four callbacks in one value because they always travel together and always
 * come from the same place, and because a paragraph inside a quotation inside a
 * section should not have to name all four at every hop on the way down.
 *
 * The block index is filled in on the way past, so nothing below the section has
 * to know where it sits in one.
 */
@Stable
internal class PassageActions(
    /** A tap: the sentence under the finger. */
    val onSentence: (blockIndex: Int, sentence: Sentence, paragraph: String) -> Unit =
        { _, _, _ -> },
    /** A long press: the single word under it. */
    val onWord: (blockIndex: Int, sentence: Sentence, word: Word, paragraph: String) -> Unit =
        { _, _, _, _ -> },
    /** The whole paragraph, as one passage. */
    val onParagraph: (blockIndex: Int, paragraph: String) -> Unit = { _, _ -> },
    /** Everything in a block, when the reader asks for what is on screen. */
    val onBlock: (blockIndex: Int, sentences: List<Sentence>, paragraph: String) -> Unit =
        { _, _, _ -> },
    /** A tap that landed on no words, which is a tap on the page rather than on text. */
    val onElsewhere: () -> Unit = {},
) {

    /** The same actions, with the block already named. */
    fun forBlock(blockIndex: Int) = BlockActions(
        onSentence = { sentence, paragraph -> onSentence(blockIndex, sentence, paragraph) },
        onWord = { sentence, word, paragraph -> onWord(blockIndex, sentence, word, paragraph) },
        onParagraph = { paragraph -> onParagraph(blockIndex, paragraph) },
        onBlock = { sentences, paragraph -> onBlock(blockIndex, sentences, paragraph) },
        onElsewhere = onElsewhere,
    )

    companion object {
        val None = PassageActions()
    }
}

/** [PassageActions] once it knows which block it belongs to. */
internal class BlockActions(
    val onSentence: (Sentence, paragraph: String) -> Unit,
    val onWord: (Sentence, Word, paragraph: String) -> Unit,
    val onParagraph: (paragraph: String) -> Unit,
    val onBlock: (List<Sentence>, paragraph: String) -> Unit,
    val onElsewhere: () -> Unit,
)

/**
 * One section of a reflowable book, set at the reader's chosen size.
 *
 * Everything here is measured against [style] rather than given a size of its
 * own: a heading is the body size scaled up, the space between blocks is a
 * proportion of the leading. That is what makes the text size setting act on the
 * whole page instead of only on its paragraphs - a book at 200% with the same
 * gaps it had at 100% reads as text that has outgrown its own layout.
 *
 * [translations] is what has become of the section's own sentences, and
 * [actions] is how a passage somebody pointed at gets back to whoever can
 * translate it. Both default to nothing, so this composable can still be drawn -
 * in a test, or in a preview - with no translator behind it.
 *
 * Each block is handed only its own slice. That is what keeps translating one
 * sentence from being work for the rest of the chapter: a block whose slice
 * compares equal to the one it was given last time is not composed again at all.
 */
@Composable
internal fun TextSectionView(
    section: TextSection,
    style: TextStyle,
    modifier: Modifier = Modifier,
    translations: SectionTranslations = SectionTranslations.None,
    actions: PassageActions = PassageActions.None,
    /** Whether what is on screen should be translated without being asked twice. */
    translateOnScreen: TranslateOnScreen = TranslateOnScreen.Idle,
    /** What a tap and a long press ask for, which is the reader's own setting. */
    gestures: TranslationGestures = TranslationGestures.Default,
    /**
     * Whether the type is being resized right now - a pinch in progress.
     *
     * While it is, a block changing height is the reader's fingers rather than a
     * translation landing, and the two want opposite things from an animation.
     */
    resizingType: Boolean = false,
) {
    val spacing = with(LocalDensity.current) {
        (style.fontSize * BLOCK_SPACING).toDp()
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(spacing),
        modifier = modifier,
    ) {
        section.blocks.forEachIndexed { index, block ->
            TextBlockView(
                block = block,
                style = style,
                translations = translations.block(index),
                actions = actions.forBlock(index),
                translateOnScreen = translateOnScreen,
                gestures = gestures,
                resizingType = resizingType,
            )
        }
    }
}

@Composable
private fun TextBlockView(
    block: TextBlock,
    style: TextStyle,
    translations: SentenceTranslations,
    actions: BlockActions,
    translateOnScreen: TranslateOnScreen,
    gestures: TranslationGestures,
    resizingType: Boolean,
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
        is TextBlock.Figure -> FigureBlock(block)
        TextBlock.Separator -> SeparatorBlock()
    }
}
