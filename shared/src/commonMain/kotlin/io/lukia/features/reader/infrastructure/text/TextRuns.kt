package io.lukia.features.reader.infrastructure.text

import io.lukia.features.reader.domain.Emphasis
import io.lukia.features.reader.domain.TextRun

/**
 * Adds [text], merged into the run before it when the formatting has not
 * changed.
 *
 * Every one of these parsers arrives at a paragraph in pieces - a chapter split
 * across source lines, a Word paragraph split at every formatting boundary - and
 * without merging, a paragraph of unemphasised prose becomes dozens of identical
 * runs that the reader then has to style and lay out one at a time.
 */
internal fun MutableList<TextRun>.append(text: String, emphasis: Set<Emphasis>) {
    if (text.isEmpty()) return

    val last = lastOrNull()

    if (last != null && last.emphasis == emphasis) {
        this[lastIndex] = last.copy(text = last.text + text)
    } else {
        add(TextRun(text = text, emphasis = emphasis))
    }
}

/**
 * The runs with the space at either end taken off, since that space came from
 * how the file was laid out rather than from the sentence.
 */
internal fun List<TextRun>.trimmedEnds(): List<TextRun> {
    if (isEmpty()) return emptyList()

    val trimmed = toMutableList()

    trimmed[0] = trimmed[0].copy(text = trimmed[0].text.trimStart())
    trimmed[trimmed.lastIndex] = trimmed.last().copy(text = trimmed.last().text.trimEnd())

    return trimmed.filter { it.text.isNotEmpty() }
}
