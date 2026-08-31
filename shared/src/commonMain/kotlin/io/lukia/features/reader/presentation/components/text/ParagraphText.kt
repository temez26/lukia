package io.lukia.features.reader.presentation.components.text

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import io.lukia.features.reader.domain.text.Emphasis
import io.lukia.features.reader.domain.text.TextRun
import io.lukia.features.translate.domain.text.Sentence
import io.lukia.features.translate.domain.text.Word

/**
 * A paragraph as it is actually drawn, and where each of its sentences and
 * words ended up in it.
 *
 * The ranges travel with the text because replacing anything moves everything
 * after it. A tap gives back an offset into the text on screen, and without
 * these ranges that offset would be read against the paragraph the book wrote
 * rather than the one the reader is looking at - so the first translation in a
 * paragraph would quietly break tapping on everything below it.
 *
 * [sentences] and [words] are also what the marks are drawn from. Nothing here
 * carries a colour: which passages are marked, and how, changes on every tap,
 * and baking that into the text would mean rebuilding and re-laying out the
 * whole paragraph each time somebody pointed at a word in it.
 */
@Immutable
internal class ParagraphText(
    val text: AnnotatedString,
    val sentences: List<DrawnSpan>,
    val words: List<DrawnWord>,
) {

    /** Which sentence is at [offset] in the drawn text, if any. */
    fun sentenceAt(offset: Int): Int? = sentences.firstOrNull { it.covers(offset) }?.index

    /** Which word is at [offset] in the drawn text, if any. */
    fun wordAt(offset: Int): DrawnWord? = words.firstOrNull { it.span.covers(offset) }

    /** One passage, and the stretch of the drawn paragraph it occupies. */
    @Immutable
    internal data class DrawnSpan(val index: Int, val start: Int, val end: Int) {
        fun covers(offset: Int): Boolean = offset >= start && offset < end
    }

    /**
     * A word of a sentence, drawn. Both indices are kept because a word is
     * addressed through its sentence - see [Word].
     */
    @Immutable
    internal data class DrawnWord(val sentenceIndex: Int, val span: DrawnSpan)
}

/**
 * The words to draw in place of a passage the book wrote, or null to leave the
 * book's own there.
 *
 * One function for both units rather than two, because splicing a word and
 * splicing a sentence are the same operation on different ranges - and because
 * whoever answers it has to be able to say "this sentence is translated, so its
 * words are not on the page any more" in one place.
 */
internal fun interface ShownTranslation {

    /** [word] is null when the whole sentence is being asked about. */
    fun of(sentence: Sentence, word: Word?): String?
}

/**
 * Sets a paragraph, with any translated sentence - or any single translated
 * word - drawn in place of the text it stands for.
 *
 * Splicing at this level rather than swapping whole paragraphs is what keeps a
 * translated sentence *in* its paragraph: the lines around it stay where they
 * were, and the emphasis the author put on the words either side of it survives
 * - which a paragraph flattened to plain text and rebuilt would lose.
 *
 * A translated sentence is set in the paragraph's own face rather than in the
 * runs it replaces. Emphasis inside a sentence belongs to the particular words
 * that carried it, and carrying "the third word was italic" across into a
 * language whose third word is somewhere else entirely would be inventing a
 * claim the author never made. A single word is different: it replaces exactly
 * one run's worth of text, so it keeps whatever that word was set in.
 *
 * A sentence translated as a whole has no words of its own left on the page, so
 * none are recorded for it - which is also what stops a long press inside a
 * translated sentence from offering to translate a word of the translation.
 */
internal fun buildParagraphText(
    runs: List<TextRun>,
    sentences: List<Sentence>,
    words: List<Word>,
    /**
     * The whole paragraph in place of all of it, when that is what was asked
     * about. Nothing inside it is drawn then, and nothing inside it is
     * addressable - a paragraph replaced whole has no sentences of its own left
     * on the page for a tap to land on.
     */
    block: String? = null,
    shown: ShownTranslation,
): ParagraphText {
    if (block != null) {
        return ParagraphText(
            text = AnnotatedString(block),
            sentences = listOf(ParagraphText.DrawnSpan(WHOLE_BLOCK, 0, block.length)),
            words = emptyList(),
        )
    }

    val slices = runs.runSlices()
    val length = slices.lastOrNull()?.end ?: 0
    // Grouped once. Searching the whole list per sentence would make setting a
    // paragraph cost the square of what is written in it.
    val wordsBySentence = words.groupBy { it.sentenceIndex }
    val drawnSentences = mutableListOf<ParagraphText.DrawnSpan>()
    val drawnWords = mutableListOf<ParagraphText.DrawnWord>()

    val text = buildAnnotatedString {
        var offset = 0

        sentences.forEach { sentence ->
            if (sentence.start >= length) return@forEach

            // Whatever sits between the last sentence and this one - the spaces
            // after a full stop - belongs to neither and is drawn untouched.
            appendSlices(slices, offset, sentence.start)

            val start = this.length
            val end = sentence.end.coerceAtMost(length)
            val whole = shown.of(sentence, null)

            if (whole != null) {
                append(whole)
            } else {
                appendWords(
                    slices = slices,
                    sentence = sentence,
                    words = wordsBySentence[sentence.index].orEmpty(),
                    end = end,
                    shown = shown,
                    drawn = drawnWords,
                )
            }

            drawnSentences += ParagraphText.DrawnSpan(sentence.index, start, this.length)
            offset = end
        }

        appendSlices(slices, offset, length)
    }

    return ParagraphText(text = text, sentences = drawnSentences, words = drawnWords)
}

/**
 * One sentence's own text, word by word, so that a single translated word can
 * take the place of the one it stands for.
 *
 * Every word is recorded whether or not anything was put in its place: a long
 * press has to find the word under it before there is any translation to find.
 */
private fun AnnotatedString.Builder.appendWords(
    slices: List<RunSlice>,
    sentence: Sentence,
    words: List<Word>,
    end: Int,
    shown: ShownTranslation,
    drawn: MutableList<ParagraphText.DrawnWord>,
) {
    var offset = sentence.start

    words.forEach { word ->
        if (word.start >= end) return@forEach

        // The punctuation and spacing around a word are not part of it.
        appendSlices(slices, offset, word.start)

        val start = this.length
        val wordEnd = word.end.coerceAtMost(end)
        val translation = shown.of(sentence, word)

        if (translation == null) {
            appendSlices(slices, word.start, wordEnd)
        } else {
            append(translation)
        }

        drawn += ParagraphText.DrawnWord(
            sentenceIndex = sentence.index,
            span = ParagraphText.DrawnSpan(word.index, start, this.length),
        )

        offset = wordEnd
    }

    appendSlices(slices, offset, end)
}

/**
 * The index a paragraph translated whole is filed under.
 *
 * Not a sentence of the book: it stands for all of them, and the one thing that
 * must not happen is a tap on it being read as a tap on the first sentence and
 * translating that instead. Negative because no sentence is.
 */
internal const val WHOLE_BLOCK = -1

/** The paragraph with every mark dropped - what sentence offsets are counted against. */
internal fun List<TextRun>.plain(): String = joinToString(separator = "") { it.text }

internal fun Set<Emphasis>.spanStyle(): SpanStyle = SpanStyle(
    fontWeight = if (Emphasis.Bold in this) FontWeight.Bold else null,
    fontStyle = if (Emphasis.Italic in this) FontStyle.Italic else null,
    fontFamily = if (Emphasis.Monospace in this) FontFamily.Monospace else null,
)

/** One run, and where it falls in the paragraph's plain text. */
private data class RunSlice(
    val start: Int,
    val end: Int,
    val text: String,
    val emphasis: Set<Emphasis>,
)

private fun List<TextRun>.runSlices(): List<RunSlice> {
    var start = 0

    return map { run ->
        val end = start + run.text.length
        RunSlice(start, end, run.text, run.emphasis).also { start = end }
    }
}

/**
 * Appends the original text between two offsets, cut at every run boundary it
 * crosses so each piece keeps the marks it was written with.
 */
private fun AnnotatedString.Builder.appendSlices(slices: List<RunSlice>, from: Int, to: Int) {
    if (from >= to) return

    slices.forEach { slice ->
        val start = maxOf(from, slice.start)
        val end = minOf(to, slice.end)
        if (start >= end) return@forEach

        val text = slice.text.substring(start - slice.start, end - slice.start)

        if (slice.emphasis.isEmpty()) {
            append(text)
        } else {
            withStyle(slice.emphasis.spanStyle()) {
                append(text)
            }
        }
    }
}
