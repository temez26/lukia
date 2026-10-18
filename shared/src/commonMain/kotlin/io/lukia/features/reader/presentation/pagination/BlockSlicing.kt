package io.lukia.features.reader.presentation.pagination

import io.lukia.features.reader.domain.text.TextBlock
import io.lukia.features.reader.domain.text.TextRun
import io.lukia.features.reader.domain.text.splitIntoSentences

/**
 * A block cut in two at a character offset, for a block too tall to fit on the
 * page it landed on.
 *
 * A last resort rather than how a page is normally filled - pages are made of
 * whole paragraphs wherever one fits. But a book is not obliged to be made of
 * paragraphs: generated output carries single "paragraphs" of tens of thousands
 * of characters, and refusing to cut those shows a page taller than any screen
 * with no way to see the bottom of it.
 */
internal fun TextBlock.cutAt(offset: Int): Pair<TextBlock, TextBlock>? = when (this) {
    // The rest of a chapter title carried onto the next page opens nothing.
    is TextBlock.Heading -> text.cutAt(offset)?.let { (head, tail) ->
        copy(text = head) to copy(text = tail, chapter = TextBlock.ChapterRole.None)
    }

    is TextBlock.Paragraph -> text.cutAt(offset)?.let { (head, tail) ->
        copy(text = head) to copy(text = tail)
    }

    is TextBlock.Quote -> text.cutAt(offset)?.let { (head, tail) ->
        copy(text = head) to copy(text = tail)
    }

    // The continuation of a list item shows no marker: the bullet belongs to the
    // item, and drawing it again on the next page reads as a second item.
    is TextBlock.ListItem -> text.cutAt(offset)?.let { (head, tail) ->
        copy(text = head) to copy(text = tail, marker = "")
    }

    is TextBlock.Code -> text.cutText(offset)?.let { (head, tail) ->
        TextBlock.Code(head) to TextBlock.Code(tail)
    }

    // An illustration and a scene break are single marks. There is nothing
    // inside either of them to cut between.
    else -> null
}

/**
 * How many of a block's sentences are behind [offset].
 *
 * What a slice needs to say which sentences are on it, so a sentence translated
 * on the page it ends on is the same sentence to the book as on the page it
 * started. A cut inside a sentence counts it to both halves - each page really
 * does hold part of it, and otherwise one half has no sentence at all under the
 * reader's finger.
 */
internal fun sentencesBefore(text: String, offset: Int): Int =
    splitIntoSentences(text).count { it.end <= offset }

private fun List<TextRun>.cutAt(offset: Int): Pair<List<TextRun>, List<TextRun>>? {
    if (offset <= 0) return null

    val head = mutableListOf<TextRun>()
    val tail = mutableListOf<TextRun>()
    var consumed = 0

    forEach { run ->
        val start = consumed
        val end = consumed + run.text.length
        consumed = end

        when {
            end <= offset -> head += run
            start >= offset -> tail += run

            else -> {
                head += run.copy(text = run.text.substring(0, offset - start))
                tail += run.copy(text = run.text.substring(offset - start))
            }
        }
    }

    if (tail.isEmpty()) return null

    return head.toList() to tail.toList()
}

/**
 * Preformatted text is cut at the line the offset falls in rather than at the
 * offset itself: half a line of code on each of two pages is neither of them
 * readable, and the lines are the one thing about it that must not move.
 */
private fun String.cutText(offset: Int): Pair<String, String>? {
    if (offset <= 0 || offset >= length) return null

    val breakAt = lastIndexOf('\n', offset - 1)
    if (breakAt < 0) return null

    return substring(0, breakAt) to substring(breakAt + 1)
}
