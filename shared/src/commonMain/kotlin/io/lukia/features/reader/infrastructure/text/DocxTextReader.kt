package io.lukia.features.reader.infrastructure.text

import io.lukia.core.infrastructure.archive.ZipArchiveReader
import io.lukia.core.infrastructure.archive.resolveArchivePath
import io.lukia.core.infrastructure.xml.XmlEvent
import io.lukia.core.infrastructure.xml.xmlEvents
import io.lukia.features.reader.domain.Emphasis
import io.lukia.features.reader.domain.ReflowableContent
import io.lukia.features.reader.domain.TextBlock
import io.lukia.features.reader.domain.TextRun
import io.lukia.features.reader.domain.isBlank

private const val DOCUMENT_ENTRY = "word/document.xml"
private const val RELATIONSHIPS_ENTRY = "word/_rels/document.xml.rels"

/** Where a relationship's target is written relative to. */
private const val DOCUMENT_DIRECTORY = "word"

/**
 * Reads a Word document into the reader's blocks.
 *
 * A `.docx` is a ZIP whose `word/document.xml` is the document: a flat run of
 * paragraphs, each carrying its style by name and its text split into runs that
 * change whenever the formatting does. That flatness is the whole difficulty -
 * unlike an EPUB, the file states no divisions at all, so where one section ends
 * and the next begins has to be read off the headings, which is what
 * [sectionText] does.
 *
 * What is deliberately not read: page geometry, columns, headers and footers,
 * fields, and the numbering definitions that would give an ordered list its real
 * numbers. Those describe a document as printed, and this reader is re-setting
 * it to be read on a screen at whatever size the reader asked for - a page
 * break from a file that was laid out for A4 means nothing here.
 *
 * Returns null when the archive holds no document part, which is the one thing
 * that makes a file not a `.docx` at all.
 */
internal fun readDocx(docxBytes: ByteArray): ReflowableContent? {
    val zip = ZipArchiveReader(docxBytes)
    val documentXml = zip.readEntry(DOCUMENT_ENTRY)?.let(::decodeText) ?: return null

    val media = zip.relationshipTargets()

    val blocks = docxBlocks(documentXml) { relationshipId ->
        media[relationshipId]?.let(zip::readEntry)
    }

    if (blocks.isEmpty()) return null

    val sectioned = sectionText(blocks)

    return ReflowableContent(sections = sectioned.sections, outline = sectioned.outline)
}

/**
 * Every relationship the document declares, as id to archive entry.
 *
 * Read once up front rather than looked up per drawing: a drawing names a
 * relationship id and never a file, so this table is the only way to find a
 * picture - and an illustrated document would otherwise re-parse it a hundred
 * times over.
 */
private fun ZipArchiveReader.relationshipTargets(): Map<String, String> {
    val rels = readEntry(RELATIONSHIPS_ENTRY)?.let(::decodeText) ?: return emptyMap()

    return xmlEvents(rels)
        .filterIsInstance<XmlEvent.Open>()
        .filter { it.name == "relationship" }
        .mapNotNull { relationship ->
            val id = relationship.attributes["id"] ?: return@mapNotNull null
            val target = relationship.attributes["target"] ?: return@mapNotNull null

            id to resolveArchivePath(DOCUMENT_DIRECTORY, target)
        }
        .toMap()
}

/**
 * Paragraph styles that mean something structural. Matched on the style id with
 * spaces removed, because the same style is written `Heading1` by one producer
 * and `heading 1` by another.
 */
private fun headingLevelOf(style: String): Int? = when {
    style.startsWith("heading") -> style.removePrefix("heading").toIntOrNull()?.coerceAtMost(6)
    style == "title" -> 1
    style == "subtitle" -> 2
    else -> null
}

private fun isQuoteStyle(style: String): Boolean = style.endsWith("quote")

private class DocxParagraph {
    val runs = mutableListOf<TextRun>()
    var style: String = ""
    var isListItem: Boolean = false
}

/**
 * Walks the document part once, gathering each `w:p` into a block.
 *
 * Word writes a paragraph's properties (`w:pPr`) before its text, so by the time
 * any run arrives the style is already known - which is what lets this decide
 * what a paragraph is at the point it ends rather than having to look ahead.
 */
private fun docxBlocks(xml: String, image: (relationshipId: String) -> ByteArray?): List<TextBlock> {
    val blocks = mutableListOf<TextBlock>()

    var paragraph: DocxParagraph? = null
    var emphasis = emptySet<Emphasis>()
    var inRunProperties = false
    var inText = false

    // Field instructions and deleted text are both stored as ordinary text and
    // are both invisible in Word - a `PAGEREF` instruction read aloud in the
    // middle of a sentence is not something the author wrote.
    var hiddenDepth = 0

    xmlEvents(xml).forEach { event ->
        when (event) {
            is XmlEvent.Open -> when (event.name) {
                "p" -> paragraph = DocxParagraph()

                "instrtext", "del" -> if (!event.selfClosing) hiddenDepth++

                "pstyle" -> paragraph?.style =
                    event.attributes["w:val"].orEmpty().lowercase().replace(" ", "")

                "numpr" -> paragraph?.isListItem = true

                // A run's formatting comes only from its own properties, so both
                // the run and its property block start from nothing. Without the
                // reset on `w:r`, a run with no properties of its own would
                // inherit whatever the paragraph mark was formatted as.
                "r" -> emphasis = emptySet()

                "rpr" -> {
                    inRunProperties = !event.selfClosing
                    emphasis = emptySet()
                }

                "b" -> if (inRunProperties && event.attributes.isOn()) {
                    emphasis = emphasis + Emphasis.Bold
                }

                "i" -> if (inRunProperties && event.attributes.isOn()) {
                    emphasis = emphasis + Emphasis.Italic
                }

                // `<w:t/>` with nothing in it has no closing tag to switch this
                // back off again.
                "t" -> inText = !event.selfClosing && hiddenDepth == 0

                // Word's own line and tab characters, which are elements rather
                // than characters in the text.
                "br" -> paragraph?.runs?.append("\n", emphasis)
                "tab" -> paragraph?.runs?.append("\t", emphasis)

                "blip" -> {
                    val bytes = event.attributes["r:embed"]?.let(image)

                    if (bytes != null) {
                        blocks += TextBlock.Figure(bytes = bytes, description = null)
                    }
                }
            }

            is XmlEvent.Close -> when (event.name) {
                "p" -> {
                    paragraph?.let { blocks.addParagraph(it) }
                    paragraph = null

                    // A run's formatting does not survive the paragraph it was
                    // in, and neither does a paragraph left open by a malformed
                    // file.
                    emphasis = emptySet()
                }

                "instrtext", "del" -> if (hiddenDepth > 0) hiddenDepth--

                "rpr" -> inRunProperties = false

                "t" -> inText = false
            }

            is XmlEvent.Text -> if (inText) paragraph?.runs?.append(event.text, emphasis)
        }
    }

    return blocks
}

/**
 * Word writes a toggle both as a bare `<w:b/>` meaning on and as `<w:b w:val="0"/>`
 * meaning off, and the second is how a style's bold is switched back off for one
 * run inside it.
 */
private fun Map<String, String>.isOn(): Boolean =
    when (this["w:val"]?.lowercase()) {
        "0", "false", "off" -> false
        else -> true
    }

private fun MutableList<TextBlock>.addParagraph(paragraph: DocxParagraph) {
    val runs = paragraph.runs.toList()

    // An empty paragraph is how Word writes vertical space, and carrying every
    // one of them over would fill the reader with gaps that meant nothing once
    // the text was re-set at a different size.
    if (runs.isBlank()) return

    val headingLevel = headingLevelOf(paragraph.style)

    add(
        when {
            headingLevel != null -> TextBlock.Heading(text = runs, level = headingLevel)

            // Checked after the heading, since a numbered heading carries both.
            paragraph.isListItem -> TextBlock.ListItem(text = runs, marker = LIST_MARKER)

            isQuoteStyle(paragraph.style) -> TextBlock.Quote(runs)

            else -> TextBlock.Paragraph(runs)
        }
    )
}

/**
 * Every list item gets a bullet, including the ones in a numbered list.
 *
 * A list's real numbers live in a separate numbering part, as a definition that
 * has to be resolved through an abstract definition, a level, an override chain
 * and a restart rule before the first item can be called "1." - and getting any
 * step of that wrong produces a list that is confidently misnumbered, which is
 * worse than a list of bullets. The items and their order, which is what the
 * text depends on, are both kept.
 */
private const val LIST_MARKER = "•"
