package io.lukia.features.reader.infrastructure.text

import io.lukia.core.domain.diagnostics.Diagnostics
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
internal fun readPlainText(
    bytes: ByteArray,
    diagnostics: Diagnostics = Diagnostics.None,
): ReflowableContent {
    val blocks = decodeText(bytes, diagnostics)
        .splitParagraphs()
        .map { TextBlock.Paragraph(listOf(TextRun(it))) }

    // The outline comes back empty rather than null: plain text genuinely has
    // no chapters to read, which is a different thing from not being able to
    // look for them.
    return reflowableFrom(blocks)
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
    split(Regex("\n[ \t]*\n+"))
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

            when {
                KeepsOwnBreak.containsMatchIn(line) -> append("\n")

                // A word the writer's editor broke across the wrap is one word,
                // and joining its halves with a space hands the reader - and a
                // translator - "exam- ple". The same rule a PDF's lines follow;
                // it was only ever written down on that side.
                endsWithBreakingHyphen() -> closeUpSoftHyphen()

                else -> append(" ")
            }

            append(line.trimEnd())
        }
    }.trim()
}

/**
 * A soft hyphen is the one that goes: it exists to say where a word *may* be
 * broken, and once the break has been undone there is nothing left for it to
 * say. An ordinary hyphen stays, because nothing here can tell "exam-ple" from
 * "well-known" and dropping one out of the second is the worse mistake.
 */
private fun StringBuilder.closeUpSoftHyphen() {
    if (last() == SOFT_HYPHEN) deleteAt(lastIndex)
}

private fun StringBuilder.endsWithBreakingHyphen(): Boolean =
    when (lastOrNull()) {
        '-', '\u2010', SOFT_HYPHEN -> true
        else -> false
    }

/**
 * Written as an escape rather than as itself, for the reason the Windows-1252
 * table beside it is: a soft hyphen is invisible, and a constant nobody can
 * read is one nobody can check.
 */
private const val SOFT_HYPHEN = '\u00AD'
