package io.lukia.features.reader.domain

/**
 * A document's outline is not always a table of contents.
 *
 * Word processors keep their own bookmarks in the file - the anchor a
 * cross-reference points at, the one a pasted DDE link left behind, the ones a
 * generated table of contents hangs off - and several of them export those into
 * the PDF's outline alongside the real headings. They come out as entries named
 * `__DdeLink__2128_1572349441` or `_Toc148392017`: machine names, in a list a
 * person is meant to read.
 *
 * These are recognised by shape rather than by a list of names. Every one is a
 * known tool's prefix followed by an identifier and no spaces at all, which is
 * what a heading someone wrote never looks like.
 */
private val ProducerBookmark = Regex(
    "^(?:_{1,2}(?:DdeLink|RefHeading|RefNumPara|RefNumParaDoc|UnoMark|Fieldmark|" +
        "Toc|Tos|Ref|Hlk|Hlt|GoBack|Int|MailOriginal|Mendeley)|OLE_LINK)[\\w.\\-]*$",
    RegexOption.IGNORE_CASE
)

/**
 * The outline as a table of contents: the producing tool's own bookmarks
 * dropped, and the nesting closed up behind them.
 *
 * Applied to every platform's outline rather than to the one that reads the file
 * by hand, because this is the document's doing and not the reader's - PDFBox,
 * PDFKit and this app's own parser all report faithfully what the file says.
 */
fun List<OutlineEntry>.asTableOfContents(): List<OutlineEntry> =
    filterNot { ProducerBookmark.matches(it.title) }.withClosedUpLevels()

/**
 * Renumbers levels so that each entry sits one step below the entry it hangs
 * from, whatever depth the file gave it.
 *
 * Dropping an entry can leave its sections behind, and a section indented under
 * a chapter that is no longer there reads as belonging to whatever now precedes
 * it - or, worse, to nothing. This is also the repair for a file that simply
 * skips a level, which producers do.
 */
private fun List<OutlineEntry>.withClosedUpLevels(): List<OutlineEntry> {
    // The levels, as the file gave them, of the entries this one is inside.
    val ancestors = ArrayDeque<Int>()

    return map { entry ->
        while (ancestors.isNotEmpty() && ancestors.last() >= entry.level) {
            ancestors.removeLast()
        }

        val level = ancestors.size
        ancestors.addLast(entry.level)

        if (level == entry.level) entry else entry.copy(level = level)
    }
}
