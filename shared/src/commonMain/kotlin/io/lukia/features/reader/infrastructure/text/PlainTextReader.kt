package io.lukia.features.reader.infrastructure.text

import io.lukia.features.reader.domain.ReflowableContent
import io.lukia.features.reader.domain.text.TextBlock
import io.lukia.features.reader.domain.text.TextRun

/**
 * Reads a plain text file as paragraphs.
 *
 * All this format states is where the line breaks are, which leaves one real
 * decision: whether a line break ends a paragraph. It cannot, because the
 * overwhelming majority of `.txt` books are hard-wrapped at some column the
 * writer's editor chose - honouring every break in one of those produces a book
 * of one-line paragraphs with a ragged edge that no longer matches the width it
 * is being read at. So a paragraph ends at a blank line, and single breaks
 * inside one are the wrapping they almost always are.
 *
 * A file that is not wrapped that way loses nothing: its paragraphs are already
 * separated by blank lines, or they are single long lines, and both come out
 * right.
 */
internal fun readPlainText(bytes: ByteArray): ReflowableContent {
    val blocks = decodeText(bytes)
        .splitParagraphs()
        .map { TextBlock.Paragraph(listOf(TextRun(it))) }

    val sectioned = sectionText(blocks)

    return ReflowableContent(
        sections = sectioned.sections,
        // Empty rather than null: plain text genuinely has no chapters to read,
        // which is a different thing from not being able to look for them.
        outline = sectioned.outline,
    )
}

/**
 * Paragraphs, split on blank lines, with the wrapping inside each one undone.
 *
 * A line that is indented or that begins with something bullet-like keeps its
 * own break. That covers the two places a hard break in a text file is
 * deliberate rather than incidental - verse, and lists - and costs nothing
 * anywhere else, since ordinary prose is flush left.
 */
private fun String.splitParagraphs(): List<String> =
    replace("\r\n", "\n")
        .replace('\r', '\n')
        .split(Regex("\n[ \t]*\n+"))
        .map { paragraph -> paragraph.unwrap() }
        .filter { it.isNotBlank() }

private val KeepsOwnBreak = Regex("""^([ \t]+|[-*•>]\s|\d+[.)]\s)""")

private fun String.unwrap(): String {
    val lines = split('\n')

    return buildString {
        lines.forEachIndexed { index, line ->
            if (index == 0) {
                append(line.trimEnd())
                return@forEachIndexed
            }

            append(if (KeepsOwnBreak.containsMatchIn(line)) "\n" else " ")
            append(line.trimEnd())
        }
    }.trim()
}
