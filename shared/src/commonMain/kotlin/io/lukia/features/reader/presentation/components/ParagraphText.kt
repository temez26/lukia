package io.lukia.features.reader.presentation.components

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import io.lukia.features.reader.domain.Emphasis
import io.lukia.features.reader.domain.TextRun
import io.lukia.features.translate.domain.Sentence

/**
 * A paragraph as it is actually drawn, and where each of its sentences ended up
 * in it.
 *
 * The two travel together because replacing a sentence moves every sentence
 * after it. A tap gives back an offset into the text on screen, and without
 * these ranges that offset would be read against the paragraph the book wrote
 * rather than the one the reader is looking at - so the first translation in a
 * paragraph would quietly break tapping on everything below it.
 *
 * [sentences] is also what the marks are drawn from. Nothing here carries a
 * colour: which sentences are tinted, and in what, changes on every tap, and
 * baking that into the text would mean rebuilding and re-laying out the whole
 * paragraph each time somebody pointed at a word in it.
 */
@Immutable
internal class ParagraphText(
    val text: AnnotatedString,
    val sentences: List<DrawnSentence>
) {

    /** Which sentence is at [offset] in the drawn text, if any. */
    fun sentenceAt(offset: Int): Int? =
        sentences.firstOrNull { offset >= it.start && offset < it.end }?.index

    /** One sentence, and the stretch of the drawn paragraph it occupies. */
    @Immutable
    internal data class DrawnSentence(val index: Int, val start: Int, val end: Int)
}

/**
 * Sets a paragraph, with any translated sentence drawn in place of the words it
 * stands for.
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
 * claim the author never made.
 *
 * [translationOf] returns the words to draw in a sentence's place, or null to
 * leave the book's own there.
 */
internal fun buildParagraphText(
    runs: List<TextRun>,
    sentences: List<Sentence>,
    translationOf: (Sentence) -> String?
): ParagraphText {
    val slices = runs.runSlices()
    val length = slices.lastOrNull()?.end ?: 0
    val drawn = mutableListOf<ParagraphText.DrawnSentence>()

    val text = buildAnnotatedString {
        var offset = 0

        sentences.forEach { sentence ->
            if (sentence.start >= length) return@forEach

            // Whatever sits between the last sentence and this one - the spaces
            // after a full stop - belongs to neither and is drawn untouched.
            appendSlices(slices, offset, sentence.start)

            val start = this.length
            val end = sentence.end.coerceAtMost(length)
            val translation = translationOf(sentence)

            if (translation == null) appendSlices(slices, sentence.start, end) else append(translation)

            drawn += ParagraphText.DrawnSentence(sentence.index, start, this.length)
            offset = end
        }

        appendSlices(slices, offset, length)
    }

    return ParagraphText(text = text, sentences = drawn)
}

/** The paragraph with every mark dropped - what sentence offsets are counted against. */
internal fun List<TextRun>.plain(): String = joinToString(separator = "") { it.text }

internal fun Set<Emphasis>.spanStyle(): SpanStyle = SpanStyle(
    fontWeight = if (Emphasis.Bold in this) FontWeight.Bold else null,
    fontStyle = if (Emphasis.Italic in this) FontStyle.Italic else null,
    fontFamily = if (Emphasis.Monospace in this) FontFamily.Monospace else null
)

/** One run, and where it falls in the paragraph's plain text. */
private data class RunSlice(
    val start: Int,
    val end: Int,
    val text: String,
    val emphasis: Set<Emphasis>
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

        if (slice.emphasis.isEmpty()) append(text) else withStyle(slice.emphasis.spanStyle()) {
            append(text)
        }
    }
}
