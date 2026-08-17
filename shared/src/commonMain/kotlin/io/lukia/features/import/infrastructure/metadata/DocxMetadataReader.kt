package io.lukia.features.import.infrastructure.metadata

import io.lukia.core.domain.BookMetadata
import io.lukia.core.infrastructure.archive.ZipArchiveReader
import io.lukia.core.infrastructure.epub.collapseWhitespace
import io.lukia.core.infrastructure.xml.XmlEvent
import io.lukia.core.infrastructure.xml.xmlEvents

/**
 * The core properties Word writes into every document it saves.
 *
 * A far shorter list than an EPUB's: a word processor knows who typed the file
 * and what they called it, and nothing about publishers or ISBNs. `dc:creator`
 * is the person who created the document rather than an author in the
 * publishing sense, but for a manuscript read in this app they are the same
 * person, and it is the only credit the format offers.
 *
 * `cp:keywords` is a single string of comma- or semicolon-separated words, unlike
 * an EPUB's repeated `dc:subject` elements - so it is split here rather than
 * shown to the reader as one long run-on subject.
 */
internal fun extractDocxMetadata(docxBytes: ByteArray): BookMetadata? {
    val xml = ZipArchiveReader(docxBytes)
        .readEntry(CorePropertiesPath)
        ?.decodeToString()
        ?: return null

    var title: String? = null
    var creator: String? = null
    var description: String? = null
    var keywords: String? = null

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
                val value = text.toString().collapseWhitespace()

                if (value.isNotBlank()) {
                    when (event.name) {
                        "title" -> if (title == null) title = value
                        "creator" -> if (creator == null) creator = value
                        // `dc:description` is Word's "Comments" field; `subject`
                        // is a one-line summary. Either is worth showing, and a
                        // document rarely fills in both.
                        "description" -> if (description == null) description = value
                        "subject" -> if (description == null) description = value
                        "keywords" -> if (keywords == null) keywords = value
                    }
                }

                current = null
                text.clear()
            }
        }
    }

    val metadata = BookMetadata(
        title = title,
        authors = listOfNotNull(creator),
        description = description,
        subjects = keywords.orEmpty().splitKeywords()
    )

    return metadata.takeIf { !it.isEmpty }
}

private const val CorePropertiesPath = "docProps/core.xml"

private fun String.splitKeywords(): List<String> =
    split(',', ';')
        .map { it.collapseWhitespace() }
        .filter { it.isNotBlank() }
        .distinct()
