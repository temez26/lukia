package io.lukia.features.reader.presentation.components.passages

import androidx.compose.runtime.Stable
import io.lukia.features.translate.domain.text.Sentence
import io.lukia.features.translate.domain.text.Word

/**
 * What a reader can ask of the passages in one block of a book.
 *
 * Four callbacks in one value because they always travel together and always
 * come from the same place, and because a paragraph inside a quotation should not
 * have to name all four at every hop on the way down.
 *
 * The block index is filled in on the way past, so nothing below the block has
 * to know where it sits in the book.
 */
@Stable
internal class PassageActions(
    /** A tap: the sentence under the finger. */
    val onSentence: (blockIndex: Int, sentence: Sentence, paragraph: String) -> Unit =
        { _, _, _ -> },
    /**
     * The single word under the finger, and whether the gesture that reached it
     * was a hold.
     *
     * The gesture is carried rather than resolved here because what a hold means
     * depends on something no block knows: a word that has already been answered
     * opens what else it means, and a word that has not is asked about like any
     * other passage. Only the session can tell those apart.
     */
    val onWord: (
        blockIndex: Int,
        sentence: Sentence,
        word: Word,
        paragraph: String,
        held: Boolean,
    ) -> Unit = { _, _, _, _, _ -> },
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
        onWord = { sentence, word, paragraph, held ->
            onWord(blockIndex, sentence, word, paragraph, held)
        },
        onParagraph = { paragraph -> onParagraph(blockIndex, paragraph) },
        onBlock = { sentences, paragraph -> onBlock(blockIndex, sentences, paragraph) },
        onElsewhere = onElsewhere,
    )

    companion object {
        val None = PassageActions()
    }
}

/** What is asked of a block nobody is listening to - for a preview, or a test. */
internal val NoBlockActions = PassageActions.None.forBlock(0)

/** [PassageActions] once it knows which block it belongs to. */
internal class BlockActions(
    val onSentence: (Sentence, paragraph: String) -> Unit,
    val onWord: (Sentence, Word, paragraph: String, held: Boolean) -> Unit,
    val onParagraph: (paragraph: String) -> Unit,
    val onBlock: (List<Sentence>, paragraph: String) -> Unit,
    val onElsewhere: () -> Unit,
)
