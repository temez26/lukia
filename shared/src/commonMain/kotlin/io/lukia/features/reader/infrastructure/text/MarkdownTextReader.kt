package io.lukia.features.reader.infrastructure.text

import io.lukia.core.domain.diagnostics.Diagnostics
import io.lukia.features.reader.domain.ReflowableContent
import io.lukia.features.reader.domain.text.TextBlock

/**
 * Reads a Markdown file as the document it describes.
 *
 * Markdown is read as prose with structure, not as a step towards HTML. What
 * survives is what a reader can see - headings, lists, quotations, code,
 * emphasis, rules - and what exists only to become something else is dropped: a
 * link keeps its text and loses its address, since there is nowhere in a book to
 * go, and an image keeps its alt text and loses its file, since a `.md` has no
 * container and whatever it points at was never imported alongside it.
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

/**
 * The first of the two stages a Markdown file goes through: what each line is.
 * What is written inside one is `MarkdownInline.kt`.
 */
private fun markdownBlocks(source: String): List<TextBlock> {
    val lines = source.split('\n')
    val document = MarkdownDocument()

    var index = 0

    while (index < lines.size) {
        val line = lines[index]
        val fence = CodeFence.matchEntire(line)

        index = if (fence == null) {
            document.addLine(line)
            index + 1
        } else {
            document.addFencedCode(lines, from = index + 1, fence = fence.groupValues[1])
        }
    }

    return document.finish()
}

/**
 * The blocks read so far, and the paragraph still being gathered.
 *
 * A paragraph is however many consecutive lines are not something else, joined
 * back up - the wrapping in the source file is not wrapping the reader should
 * see - so it cannot be emitted until the line that ends it arrives. That one
 * piece of carried state is why this is a type: every other block is decided and
 * added on the line it appears on.
 */
private class MarkdownDocument {

    private val blocks = mutableListOf<TextBlock>()
    private val paragraph = mutableListOf<String>()

    fun finish(): List<TextBlock> {
        flushParagraph()
        return blocks
    }

    fun addLine(line: String) {
        when {
            line.isBlank() -> flushParagraph()

            // Before the thematic break, since a run of dashes under a line of
            // text underlines it rather than dividing the page.
            paragraph.isNotEmpty() && SetextUnderline.matches(line) -> underline(line)

            ThematicBreak.matches(line) -> emit(TextBlock.Separator)

            else -> addMarked(line)
        }
    }

    /**
     * Adds the fenced block starting at [from] and returns the line after its
     * closing fence - or after the end of the file, for a fence nobody closed.
     */
    fun addFencedCode(lines: List<String>, from: Int, fence: String): Int {
        flushParagraph()

        val code = mutableListOf<String>()
        var index = from

        while (index < lines.size) {
            // Closed only by a fence of the same character and at least the same
            // length, which is what lets a block fenced with backticks hold a
            // line of tildes - or a line of backticks shorter than the fence.
            val trimmed = lines[index].trim()

            if (trimmed.length >= fence.length && trimmed.all { it == fence[0] }) {
                index++
                break
            }

            code += lines[index]
            index++
        }

        val text = code.joinToString(separator = "\n").trim('\n')

        if (text.isNotBlank()) blocks += TextBlock.Code(text)

        return index
    }

    /** The four things a line can announce itself as, or else more paragraph. */
    private fun addMarked(line: String) {
        val heading = AtxHeading.matchEntire(line)
        val bullet = BulletItem.matchEntire(line)
        val numbered = NumberedItem.matchEntire(line)
        val quote = Quoted.matchEntire(line)

        when {
            heading != null -> emit(
                TextBlock.Heading(
                    text = markdownRuns(heading.groupValues[2]),
                    level = heading.groupValues[1].length,
                ),
            )

            bullet != null -> emit(
                TextBlock.ListItem(text = markdownRuns(bullet.groupValues[1]), marker = "\u2022"),
            )

            numbered != null -> emit(
                TextBlock.ListItem(
                    text = markdownRuns(numbered.groupValues[2]),
                    // The document's own number, not a count: a list that starts
                    // at 4, or that repeats 1., is written that way on purpose
                    // often enough to be worth honouring.
                    marker = "${numbered.groupValues[1]}.",
                ),
            )

            quote != null -> emit(TextBlock.Quote(markdownRuns(quote.groupValues[1])))

            else -> paragraph += line.trim()
        }
    }

    /** A `===` or `---` under a paragraph makes a heading of what it underlines. */
    private fun underline(line: String) {
        val text = takeParagraph()

        blocks += TextBlock.Heading(
            text = markdownRuns(text),
            level = if (line.trimStart().startsWith('=')) 1 else 2,
        )
    }

    private fun emit(block: TextBlock) {
        flushParagraph()
        blocks += block
    }

    private fun flushParagraph() {
        val text = takeParagraph()

        if (text.isNotEmpty()) blocks += TextBlock.Paragraph(markdownRuns(text))
    }

    private fun takeParagraph(): String {
        val text = paragraph.joinToString(separator = " ").trim()
        paragraph.clear()
        return text
    }
}
