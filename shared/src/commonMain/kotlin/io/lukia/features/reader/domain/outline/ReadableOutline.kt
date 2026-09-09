package io.lukia.features.reader.domain.outline

/**
 * A document's outline is not always a table of contents.
 *
 * Word processors keep their own bookmarks in the file - a cross-reference
 * anchor, what a pasted DDE link left behind, what a generated contents hangs
 * off - and several export them into the PDF's outline alongside the real
 * headings, as `__DdeLink__2128_1572349441` or `_Toc148392017`: machine names in
 * a list a person is meant to read.
 *
 * Recognised by shape rather than by a list of names: a known tool's prefix,
 * then an identifier with no spaces at all, which is what a heading somebody
 * wrote never looks like.
 */
private val ProducerBookmark = Regex(
    "^(?:_{1,2}(?:DdeLink|RefHeading|RefNumPara|RefNumParaDoc|UnoMark|Fieldmark|" +
        "Toc|Tos|Ref|Hlk|Hlt|GoBack|Int|MailOriginal|Mendeley)|OLE_LINK)[\\w.\\-]*$",
    RegexOption.IGNORE_CASE,
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

/**
 * What this page sits under, according to the book's own table of contents.
 *
 * The nearest heading at or before the page rather than the top-level chapter
 * containing it: a section title is the more specific and more useful answer -
 * "Closures" settles more about a paragraph than "Functions" does.
 *
 * Null when the book declares no contents, or the page comes before the first
 * entry: a title page belongs to no chapter, and claiming the first would invent
 * something the book did not say.
 */
fun List<OutlineEntry>.chapterCovering(pageIndex: Int): String? =
    filter { it.pageIndex <= pageIndex }
        .maxByOrNull { it.pageIndex }
        ?.title
        ?.takeIf { it.isNotBlank() }
