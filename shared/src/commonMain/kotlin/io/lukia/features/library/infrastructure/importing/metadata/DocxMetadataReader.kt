package io.lukia.features.library.infrastructure.importing.metadata

import io.lukia.core.domain.document.BookMetadata
import io.lukia.core.infrastructure.archive.ZipArchiveReader
import io.lukia.core.infrastructure.epub.collapseWhitespace
import io.lukia.core.infrastructure.xml.XmlEvent
import io.lukia.core.infrastructure.xml.xmlEvents

/**
 * The core properties Word writes into every document it saves.
 *
 * Far shorter than an EPUB's: a word processor knows who typed the file and what
 * they called it, and nothing about publishers or ISBNs. `dc:creator` is whoever
 * created the document rather than an author in the publishing sense, but for a
 * manuscript read here they are the same person and it is the only credit the
 * format offers.
 *
 * `cp:keywords` is one string of comma- or semicolon-separated words, unlike an
 * EPUB's repeated `dc:subject` elements, so it is split here rather than shown
 * as one run-on subject.
 */
internal fun extractDocxMetadata(docxBytes: ByteArray): BookMetadata? {
    val xml = ZipArchiveReader(docxBytes)
        .readEntry(CorePropertiesPath)
        ?.decodeToString()
        ?: return null

    val properties = CoreProperties()
    var current: String? = null
    val text = StringBuilder()

    xmlEvents(xml).forEach { event ->
        when (event) {
            is XmlEvent.Open -> if (!event.selfClosing) {
                current = event.name
                text.clear()
            }

            is XmlEvent.Text -> if (current != null) text.append(event.text)

            is XmlEvent.Close -> if (event.name == current) {
                properties.take(event.name, text.toString().collapseWhitespace())
                current = null
                text.clear()
            }
        }
    }

    return properties.asMetadata().takeIf { !it.isEmpty }
}

/**
 * The four fields worth having out of Word's core properties, taken one closed
 * element at a time.
 *
 * Separate from the walk above for the same reason the EPUB reader's Dublin Core
 * accumulator is: the walk knows where in the document it is, and this knows what
 * a document says about itself. First value wins, as it does there.
 */
private class CoreProperties {

    private var title: String? = null
    private var creator: String? = null
    private var description: String? = null
    private var keywords: String? = null

    fun take(name: String, value: String) {
        if (value.isBlank()) return

        when (name) {
            "title" -> title = title ?: value
            "creator" -> creator = creator ?: value

            // `dc:description` is Word's "Comments" field; `subject` is a
            // one-line summary. Either is worth showing, and a document rarely
            // fills in both.
            "description", "subject" -> description = description ?: value

            "keywords" -> keywords = keywords ?: value
        }
    }

    fun asMetadata(): BookMetadata = BookMetadata(
        title = title,
        authors = listOfNotNull(creator),
        description = description,
        subjects = keywords.orEmpty().splitKeywords(),
    )
}

private const val CorePropertiesPath = "docProps/core.xml"

private fun String.splitKeywords(): List<String> =
    split(',', ';')
        .map { it.collapseWhitespace() }
        .filter { it.isNotBlank() }
        .distinct()
