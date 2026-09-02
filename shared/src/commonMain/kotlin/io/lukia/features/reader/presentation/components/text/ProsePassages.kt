package io.lukia.features.reader.presentation.components.text

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextLayoutResult
import io.lukia.features.reader.presentation.components.passages.BlockActions
import io.lukia.features.translate.domain.settings.TranslationGestures
import io.lukia.features.translate.domain.text.PassageUnit
import io.lukia.features.translate.domain.text.Sentence
import io.lukia.features.translate.domain.text.Word
import io.lukia.features.translate.presentation.SentenceTranslations

// Where a finger landed in a paragraph, as a passage of the book - kept apart
// from ProseText, which draws the paragraph and knows nothing about what the
// book wrote in it.

/** What one gesture landed on, once it is known what that gesture asks for. */
internal sealed interface Pointed {

    data class AtWord(val sentence: Sentence, val word: Word) : Pointed

    data class AtSentence(val sentence: Sentence) : Pointed

    data object AtParagraph : Pointed
}

/**
 * What one paragraph of the book is made of, and how a point in it becomes a
 * passage.
 *
 * The sentences and words are the ones the *book* wrote; where each of them
 * ended up in the paragraph as it is currently drawn is [ParagraphText]'s to
 * answer, and the two are brought together only here.
 */
internal class ProsePassages(
    private val sentences: List<Sentence>,
    private val words: List<Word>,
) {

    /**
     * What the reader pointed at, for the unit the gesture is set to - or null
     * when the gesture reached nothing at all.
     *
     * A gesture that is turned off reaches nothing rather than falling back to
     * something narrower: off is off, and a long press that quietly translated a
     * sentence because the word under it could not be found would be exactly the
     * kind of thing that setting exists to stop.
     *
     * The one place a gesture does not get what it asked for is a word it cannot
     * find - the punctuation between two words belongs to neither - and there it
     * gets nothing, not the sentence around it.
     */
    fun at(
        unit: PassageUnit?,
        position: Offset,
        layout: TextLayoutResult?,
        drawn: ParagraphText,
    ): Pointed? {
        if (unit == null) return null

        val offset = layout?.getOffsetForPosition(position) ?: return null
        val pointed = drawn.sentenceAt(offset) ?: return null

        // A paragraph translated whole has none of its own sentences left on the
        // page - it is one span, filed under WHOLE_BLOCK - so a gesture inside it
        // is a gesture on the paragraph, whatever unit it was set to. Without
        // this, a tap meant to put the book's own words back looked up sentence
        // -1, found nothing, and was read as a tap on the page.
        if (pointed == WHOLE_BLOCK) return Pointed.AtParagraph

        val sentence = sentences.getOrNull(pointed) ?: return null

        return when (unit) {
            PassageUnit.Paragraph -> Pointed.AtParagraph

            PassageUnit.Sentence -> Pointed.AtSentence(sentence)

            PassageUnit.Word -> wordAt(offset, drawn, sentence)
        }
    }

    /**
     * The word at this point that the reader has already asked about, or null
     * where there is none.
     *
     * Asked before the gesture's own unit rather than instead of it, and only for
     * a hold - see [TranslationGestures.wordDetails]. A word with an answer on
     * the page is a specific thing under the finger with more to say, whatever
     * the reader has set their hold to ask about; a word nobody has looked up is
     * not, and there the gesture means what it says.
     *
     * Nothing inside a paragraph translated whole, for the same reason [at]
     * answers `WHOLE_BLOCK` as the paragraph: none of its own words are on the
     * page to be pointed at.
     */
    fun answeredWordAt(
        position: Offset,
        layout: TextLayoutResult?,
        drawn: ParagraphText,
        translations: SentenceTranslations,
    ): Pointed? {
        val offset = layout?.getOffsetForPosition(position) ?: return null
        val pointed = drawn.sentenceAt(offset)?.takeIf { it != WHOLE_BLOCK } ?: return null
        val sentence = sentences.getOrNull(pointed) ?: return null

        return wordAt(offset, drawn, sentence)?.takeIf { pointedAt ->
            translations.words(sentence.index)[pointedAt.word.index] != null
        }
    }

    /** The book's own word at a place in the paragraph as it is currently drawn. */
    private fun wordAt(
        offset: Int,
        drawn: ParagraphText,
        sentence: Sentence,
    ): Pointed.AtWord? = drawn.wordAt(offset)
        ?.let { words.at(it) }
        ?.let { Pointed.AtWord(sentence, it) }
}

/**
 * What a press and hold reached in this paragraph.
 *
 * The word the reader has already looked up comes first, so that what else it
 * means is reachable however they have set their hold - see
 * [TranslationGestures.wordDetails]. Everything else, a word nobody has asked
 * about yet included, is the gesture they set and nothing more.
 */
internal fun ProsePassages.held(
    position: Offset,
    layout: TextLayoutResult?,
    drawn: ParagraphText,
    translations: SentenceTranslations,
    gestures: TranslationGestures,
): Pointed? {
    if (gestures.wordDetails) {
        answeredWordAt(position, layout, drawn, translations)?.let { return it }
    }

    return at(gestures.longPress, position, layout, drawn)
}

/**
 * Carries out what the reader asked of this block, and says whether they asked
 * anything - which is what decides whether the gesture was answered by the text
 * or is one of the two that bring the chrome back.
 */
internal fun BlockActions.act(
    pointed: Pointed?,
    paragraph: String,
    held: Boolean = false,
): Boolean {
    when (pointed) {
        null -> return false
        Pointed.AtParagraph -> onParagraph(paragraph)
        is Pointed.AtSentence -> onSentence(pointed.sentence, paragraph)
        is Pointed.AtWord -> onWord(pointed.sentence, pointed.word, paragraph, held)
    }

    return true
}

/** The word the book wrote at a place in the paragraph as it is currently drawn. */
private fun List<Word>.at(drawn: ParagraphText.DrawnWord): Word? =
    firstOrNull { it.sentenceIndex == drawn.sentenceIndex && it.index == drawn.span.index }
