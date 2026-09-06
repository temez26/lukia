package io.lukia.features.reader.infrastructure.text

import io.lukia.features.reader.domain.text.Emphasis
import io.lukia.features.reader.domain.text.TextRun

/**
 * The emphasis inside a line.
 *
 * Marks are toggled as they are met rather than matched as opening and closing
 * pairs. Proper delimiter matching exists to settle cases like `a * b * c`,
 * where the asterisks are arithmetic; toggling gets those wrong and gets every
 * well-formed line right, which for reading a book is the trade worth making -
 * the alternative is a page of CommonMark's delimiter-run rules in the middle of
 * a reader.
 *
 * The second of the two stages a Markdown file goes through - see
 * `MarkdownTextReader`, which decides what each *line* is before this decides
 * what is inside it. Separate files because the two share nothing but the text:
 * one is about the shape of a document and this is about the shape of a sentence.
 */
internal fun markdownRuns(source: String): List<TextRun> = InlineRuns(source).read()

/**
 * One line being read a character at a time.
 *
 * Each case answers the same question - given the character at this index, what
 * is written down and where does reading resume - so each is a method returning
 * the next index, and the loop is the whole of the control flow.
 */
private class InlineRuns(private val source: String) {

    private val runs = mutableListOf<TextRun>()
    private val plain = StringBuilder()
    private var marks = emptySet<Emphasis>()

    fun read(): List<TextRun> {
        var index = 0

        while (index < source.length) {
            index = when (val char = source[index]) {
                '\\' -> escaped(index)
                '`' -> code(index)
                '!' -> image(index)
                '[' -> link(index)
                '*', '_' -> emphasised(index, char)
                else -> literal(index)
            }
        }

        flushPlain()

        return runs
    }

    /**
     * A backslash turns the punctuation after it back into itself, which is how
     * a literal asterisk is written. Before a letter it is just a backslash.
     */
    private fun escaped(index: Int): Int {
        val next = source.getOrNull(index + 1)

        if (next == null || next.isLetterOrDigit()) return literal(index)

        plain.append(next)
        return index + 2
    }

    /** A span of code, or - for a backtick nobody closed - a backtick. */
    private fun code(index: Int): Int {
        val close = source.indexOf('`', startIndex = index + 1)

        if (close < 0) return literal(index)

        flushPlain()
        runs.append(source.substring(index + 1, close), marks + Emphasis.Monospace)
        return close + 1
    }

    /**
     * The alt text, which is the only part of an image a reader that cannot open
     * the file has anything to show.
     */
    private fun image(index: Int): Int {
        if (source.getOrNull(index + 1) != '[') return literal(index)

        val link = parseLink(source, index + 1) ?: return literal(index)

        plain.append(link.text)
        return link.end
    }

    /** The words, without the address: there is nowhere in a book to go. */
    private fun link(index: Int): Int {
        val link = parseLink(source, index) ?: return literal(index)

        plain.append(link.text)
        return link.end
    }

    private fun emphasised(index: Int, char: Char): Int {
        val doubled = source.getOrNull(index + 1) == char
        val width = if (doubled) 2 else 1

        // `snake_case_names` are identifiers, not emphasis.
        if (char == '_' && isInsideWord(source, index, width)) return literal(index)

        flushPlain()

        val mark = if (doubled) Emphasis.Bold else Emphasis.Italic
        marks = if (mark in marks) marks - mark else marks + mark
        return index + width
    }

    private fun literal(index: Int): Int {
        plain.append(source[index])
        return index + 1
    }

    private fun flushPlain() {
        runs.append(plain.toString(), marks)
        plain.clear()
    }
}

private class MarkdownLink(val text: String, val end: Int)

/**
 * A `[text](target)` starting at [start], or null when what follows is not one -
 * an unmatched bracket is a bracket.
 */
private fun parseLink(source: String, start: Int): MarkdownLink? {
    val textEnd = source.indexOf(']', startIndex = start + 1)

    if (textEnd < 0 || source.getOrNull(textEnd + 1) != '(') return null

    val targetEnd = source.indexOf(')', startIndex = textEnd + 2)

    if (targetEnd < 0) return null

    return MarkdownLink(
        text = source.substring(start + 1, textEnd),
        end = targetEnd + 1,
    )
}

private fun isInsideWord(source: String, index: Int, width: Int): Boolean {
    val before = source.getOrNull(index - 1)
    val after = source.getOrNull(index + width)

    return before != null && after != null && before.isLetterOrDigit() && after.isLetterOrDigit()
}
