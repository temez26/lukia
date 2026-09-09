package io.lukia.features.reader.presentation.components.passages

import androidx.compose.runtime.Stable
import io.lukia.features.reader.domain.text.Sentence
import io.lukia.features.reader.domain.text.Word

/**
 * Which gesture reached a word, which is what decides what becomes of it.
 *
 * Carried rather than resolved where it happens, because what a gesture means
 * depends on something no block knows: whether the word has already been
 * answered. Only the session can tell that.
 */
@Stable
internal enum class WordGesture {

    /** A tap: the word is translated, put back, or asked about again. */
    Tapped,

    /**
     * A press and hold: whatever the reader set the hold to, except on a word
     * that has already been answered, where it opens what else that word means.
     */
    Held,

    /**
     * The second mouse button - two fingers on a trackpad - which asks what the
     * word means whatever has become of it.
     *
     * Apart from [Held] because the two are not the same question asked by
     * different hands. A hold is the reader's own gesture with their own setting
     * behind it, and its first job is translating; this has no setting and one
     * job, in the way that a right click anywhere else in the app opens the menu
     * belonging to the thing under it rather than doing something to it first.
     */
    Questioned,
}

/**
 * What a reader can ask of the passages in one block of a book.
 *
 * Four callbacks in one value because they travel together from the same place,
 * and a paragraph inside a quotation should not name all four at every hop down.
 *
 * The block index is filled in on the way past, so nothing below the block has
 * to know where it sits in the book.
 */
@Stable
internal class PassageActions(
    /** A tap: the sentence under the finger. */
    val onSentence: (blockIndex: Int, sentence: Sentence, paragraph: String) -> Unit =
        { _, _, _ -> },
    /** The single word under the pointer, and which gesture reached it. */
    val onWord: (
        blockIndex: Int,
        sentence: Sentence,
        word: Word,
        paragraph: String,
        gesture: WordGesture,
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
        onWord = { sentence, word, paragraph, gesture ->
            onWord(blockIndex, sentence, word, paragraph, gesture)
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
    val onWord: (Sentence, Word, paragraph: String, gesture: WordGesture) -> Unit,
    val onParagraph: (paragraph: String) -> Unit,
    val onBlock: (List<Sentence>, paragraph: String) -> Unit,
    val onElsewhere: () -> Unit,
)
