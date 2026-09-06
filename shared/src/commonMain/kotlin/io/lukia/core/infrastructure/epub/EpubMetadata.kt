package io.lukia.core.infrastructure.epub

import io.lukia.core.domain.document.BookMetadata
import io.lukia.core.infrastructure.xml.XmlEvent
import io.lukia.core.infrastructure.xml.xmlEvents

/**
 * The Dublin Core block an EPUB's package document opens with, which is where a
 * book states its title, who wrote it and who published it.
 *
 * Scoped to the `<metadata>` element rather than read from the package document
 * at large: `<title>` is a Dublin Core element in there and an ordinary one
 * everywhere else, and a walk that did not stop at `</metadata>` would happily
 * take a stylesheet's name as the book's.
 *
 * Element names arrive from [xmlEvents] already lower-cased and stripped of
 * their prefix, so `dc:creator` is matched here as `creator`. That also means an
 * EPUB using a different prefix for Dublin Core - which the specification allows
 * - is read correctly without a namespace table.
 */
internal fun readEpubMetadata(packageXml: String): BookMetadata {
    val fields = DublinCore()

    var inMetadata = false
    var current: String? = null
    var attributes: Map<String, String> = emptyMap()
    val text = StringBuilder()

    xmlEvents(packageXml).forEach { event ->
        when (event) {
            is XmlEvent.Open -> when {
                event.name == "metadata" -> inMetadata = true

                inMetadata && !event.selfClosing -> {
                    current = event.name
                    attributes = event.attributes
                    text.clear()
                }
            }

            is XmlEvent.Text -> if (inMetadata && current != null) text.append(event.text)

            is XmlEvent.Close -> when {
                event.name == "metadata" -> inMetadata = false

                inMetadata && event.name == current -> {
                    fields.take(event.name, text.toString().collapseWhitespace(), attributes)
                    current = null
                    attributes = emptyMap()
                    text.clear()
                }
            }
        }
    }

    return fields.asMetadata()
}

/**
 * The Dublin Core fields as they are found, one closed element at a time.
 *
 * Separate from the walk above because the two answer different questions: that
 * one knows where in the document it is, and this one knows what a book is made
 * of. Together in one function they were a walk that had to be read twice to see
 * either.
 */
private class DublinCore {

    private var title: String? = null
    private var publisher: String? = null
    private var published: String? = null
    private var language: String? = null
    private var description: String? = null
    private var identifier: String? = null
    private var isbn: String? = null
    private val authors = mutableListOf<String>()
    private val subjects = mutableListOf<String>()

    fun take(name: String, value: String, attributes: Map<String, String>) {
        if (value.isBlank()) return

        when (name) {
            // The two a book may state as many times as it likes.
            "creator" -> authors += value
            "subject" -> subjects += value

            "identifier" -> takeIdentifier(value, attributes)

            else -> takeSingle(name, value)
        }
    }

    /**
     * The fields a book has one of, where the first found is the book's own. A
     * package document may repeat `dc:title` for a subtitle or an alternate
     * script, and the specification says the first is the title.
     */
    private fun takeSingle(name: String, value: String) {
        when (name) {
            "title" -> title = title ?: value
            "publisher" -> publisher = publisher ?: value
            "date" -> published = published ?: value
            "language" -> language = language ?: value
            "description" -> description = description ?: value.stripMarkup()
        }
    }

    /**
     * A book carries several - a UUID the tooling generated, sometimes an ISBN as
     * well. The ISBN is the one a reader recognises, so it wins wherever the book
     * declares which is which.
     */
    private fun takeIdentifier(value: String, attributes: Map<String, String>) {
        if (attributes.declaresIsbn() || value.looksLikeIsbn()) {
            isbn = isbn ?: value.isbnDigits()
        } else {
            identifier = identifier ?: value
        }
    }

    fun asMetadata(): BookMetadata = BookMetadata(
        title = title,
        authors = authors.distinct(),
        publisher = publisher,
        published = published,
        language = language,
        description = description?.takeIf { it.isNotBlank() },
        subjects = subjects.distinct(),
        identifier = isbn ?: identifier,
    )
}

/**
 * EPUB 2 names the scheme on the element (`opf:scheme="ISBN"`); EPUB 3 does it
 * through a `refines` meta, which is more indirection than one field is worth -
 * so a `urn:isbn:` value is taken as its own declaration.
 */
private fun Map<String, String>.declaresIsbn(): Boolean =
    (this["opf:scheme"] ?: this["scheme"])?.equals("isbn", ignoreCase = true) == true

private fun String.looksLikeIsbn(): Boolean = startsWith("urn:isbn:", ignoreCase = true)

/** `urn:isbn:9780123456789` is an ISBN wearing a URN; the digits are the part to show. */
private fun String.isbnDigits(): String = substringAfterLast(':').trim()

/**
 * A description with its markup taken out.
 *
 * Publishers routinely put escaped HTML in `dc:description`, which arrives here
 * as literal tags once the entities are decoded. Left alone it would be shown to
 * the reader as `<p>` and `<br/>`.
 */
private fun String.stripMarkup(): String {
    if ('<' !in this) return this

    return xmlEvents(this)
        .filterIsInstance<XmlEvent.Text>()
        .joinToString(separator = " ") { it.text }
        .collapseWhitespace()
}

/**
 * One space wherever the source had any run of whitespace. Package documents are
 * written by hand and by generators alike, and both indent the value across
 * several lines.
 */
internal fun String.collapseWhitespace(): String =
    trim().replace(WhitespaceRun, " ")

private val WhitespaceRun = Regex("\\s+")
