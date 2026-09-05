package io.lukia.features.reader.infrastructure.text

import io.lukia.core.domain.diagnostics.Diagnostics
import io.lukia.features.reader.domain.ReflowableContent
import io.lukia.features.reader.domain.text.Emphasis
import io.lukia.features.reader.domain.text.TextBlock
import io.lukia.features.reader.domain.text.TextRun

/**
 * Reads a Markdown file as the document it describes.
 *
 * Markdown is read here as prose with structure, not as a step on the way to
 * HTML. The parts that survive are the parts a reader can see - headings, lists,
 * quotations, code, emphasis and rules - and the parts that exist only to become
 * something else are dropped: a link keeps its text and loses its address, since
 * there is nowhere in a book to go, and an image keeps its alt text and loses
 * its file, since a `.md` is a single file with no container and whatever it
 * points at was never imported alongside it.
 *
 * The dialect is the common core: what is written in a README rather than the
 * full CommonMark grammar. Tables, footnotes, reference links, HTML blocks and
 * indented code are read as the plain text they are, which is what they look
 * like anyway.
 */
internal fun readMarkdown(
    bytes: ByteArray,
    diagnostics: Diagnostics = Diagnostics.None,
): ReflowableContent = reflowableFrom(markdownBlocks(decodeText(bytes, diagnostics)))

private val ThematicBreak = Regex("""^ {0,3}([-*_])( *\1){2,} *$""")
private val AtxHeading = Regex("""^ {0,3}(#{1,6})\s+(.*?)\s*#*\s*$""")
private val BulletItem = Regex("""^ {0,3}[-*+]\s+(.*)$""")
private val NumberedItem = Regex("""^ {0,3}(\d{1,9})[.)]\s+(.*)$""")
private val Quoted = Regex("""^ {0,3}> ?(.*)$""")
private val CodeFence = Regex("""^ {0,3}(`{3,}|~{3,})(.*)$""")
private val SetextUnderline = Regex("""^ {0,3}(=+|-+) *$""")

private fun markdownBlocks(source: String): List<TextBlock> {
    val lines = source.split('\n')
    val blocks = mutableListOf<TextBlock>()

    // Gathered rather than emitted per line: a paragraph is however many
    // consecutive lines are not something else, joined back up - the wrapping in
    // the source file is not wrapping the reader should see.
    val paragraph = mutableListOf<String>()

    fun flushParagraph() {
        if (paragraph.isEmpty()) return

        val text = paragraph.joinToString(separator = " ").trim()
        paragraph.clear()

        if (text.isNotEmpty()) blocks += TextBlock.Paragraph(markdownRuns(text))
    }

    var index = 0

    while (index < lines.size) {
        val line = lines[index]

        val fence = CodeFence.matchEntire(line)

        if (fence != null) {
            flushParagraph()
            index = blocks.addFencedCode(lines, from = index + 1, fence = fence.groupValues[1])
            continue
        }

        when {
            line.isBlank() -> flushParagraph()

            // Before the thematic break, since a run of dashes under a line of
            // text underlines it rather than dividing the page.
            paragraph.isNotEmpty() && SetextUnderline.matches(line) -> {
                val text = paragraph.joinToString(separator = " ").trim()
                paragraph.clear()

                blocks += TextBlock.Heading(
                    text = markdownRuns(text),
                    level = if (line.trimStart().startsWith('=')) 1 else 2,
                )
            }

            ThematicBreak.matches(line) -> {
                flushParagraph()
                blocks += TextBlock.Separator
            }

            else -> {
                val heading = AtxHeading.matchEntire(line)
                val bullet = BulletItem.matchEntire(line)
                val numbered = NumberedItem.matchEntire(line)
                val quote = Quoted.matchEntire(line)

                when {
                    heading != null -> {
                        flushParagraph()
                        blocks += TextBlock.Heading(
                            text = markdownRuns(heading.groupValues[2]),
                            level = heading.groupValues[1].length,
                        )
                    }

                    bullet != null -> {
                        flushParagraph()
                        blocks += TextBlock.ListItem(
                            text = markdownRuns(bullet.groupValues[1]),
                            marker = "•",
                        )
                    }

                    numbered != null -> {
                        flushParagraph()
                        blocks += TextBlock.ListItem(
                            text = markdownRuns(numbered.groupValues[2]),
                            // The document's own number, not a count: a list that
                            // starts at 4, or that repeats 1., is written that way
                            // on purpose often enough to be worth honouring.
                            marker = "${numbered.groupValues[1]}.",
                        )
                    }

                    quote != null -> {
                        flushParagraph()
                        blocks += TextBlock.Quote(markdownRuns(quote.groupValues[1]))
                    }

                    else -> paragraph += line.trim()
                }
            }
        }

        index++
    }

    flushParagraph()

    return blocks
}

/**
 * Adds the fenced block starting at [from] and returns the line after its
 * closing fence - or after the end of the file, for a fence nobody closed.
 */
private fun MutableList<TextBlock>.addFencedCode(
    lines: List<String>,
    from: Int,
    fence: String,
): Int {
    val code = mutableListOf<String>()
    var index = from

    while (index < lines.size) {
        val line = lines[index]

        // Closed only by a fence of the same character and at least the same
        // length, which is what lets a block fenced with backticks hold a line
        // of tildes - or a line of backticks shorter than the fence.
        val trimmed = line.trim()

        if (trimmed.length >= fence.length && trimmed.all { it == fence[0] }) {
            index++
            break
        }

        code += line
        index++
    }

    val text = code.joinToString(separator = "\n").trim('\n')

    if (text.isNotBlank()) add(TextBlock.Code(text))

    return index
}

/**
 * The emphasis inside a line.
 *
 * Marks are toggled as they are met rather than matched as opening and closing
 * pairs. Proper delimiter matching exists to settle cases like `a * b * c`,
 * where the asterisks are arithmetic; toggling gets those wrong and gets every
 * well-formed line right, which for reading a book is the trade worth making -
 * the alternative is a page of CommonMark's delimiter-run rules in the middle of
 * a reader.
 */
private fun markdownRuns(source: String): List<TextRun> {
    val runs = mutableListOf<TextRun>()
    val plain = StringBuilder()
    var marks = emptySet<Emphasis>()
    var index = 0

    fun flushPlain() {
        runs.append(plain.toString(), marks)
        plain.clear()
    }

    while (index < source.length) {
        val char = source[index]

        when {
            // A backslash turns the punctuation after it back into itself, which
            // is how a literal asterisk is written.
            char == '\\' && index + 1 < source.length && !source[index + 1].isLetterOrDigit() -> {
                plain.append(source[index + 1])
                index += 2
            }

            char == '`' -> {
                val close = source.indexOf('`', startIndex = index + 1)

                if (close < 0) {
                    plain.append(char)
                    index++
                } else {
                    flushPlain()
                    runs.append(source.substring(index + 1, close), marks + Emphasis.Monospace)
                    index = close + 1
                }
            }

            char == '!' && source.getOrNull(index + 1) == '[' -> {
                val link = parseLink(source, index + 1)

                if (link == null) {
                    plain.append(char)
                    index++
                } else {
                    // The alt text, which is the only part of an image a reader
                    // that cannot open the file has anything to show.
                    plain.append(link.text)
                    index = link.end
                }
            }

            char == '[' -> {
                val link = parseLink(source, index)

                if (link == null) {
                    plain.append(char)
                    index++
                } else {
                    plain.append(link.text)
                    index = link.end
                }
            }

            char == '*' || char == '_' -> {
                val doubled = source.getOrNull(index + 1) == char
                val width = if (doubled) 2 else 1

                if (char == '_' && isInsideWord(source, index, width)) {
                    // `snake_case_names` are identifiers, not emphasis.
                    plain.append(char)
                    index++
                } else {
                    flushPlain()

                    val mark = if (doubled) Emphasis.Bold else Emphasis.Italic
                    marks = if (mark in marks) marks - mark else marks + mark
                    index += width
                }
            }

            else -> {
                plain.append(char)
                index++
            }
        }
    }

    flushPlain()

    return runs
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
