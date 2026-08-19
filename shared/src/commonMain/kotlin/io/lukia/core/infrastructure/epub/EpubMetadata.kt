package io.lukia.core.infrastructure.epub

import io.lukia.core.domain.BookMetadata
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
    var title: String? = null
    val authors = mutableListOf<String>()
    var publisher: String? = null
    var published: String? = null
    var language: String? = null
    var description: String? = null
    val subjects = mutableListOf<String>()
    var identifier: String? = null
    var isbn: String? = null

    var inMetadata = false
    var current: String? = null
    var currentAttributes: Map<String, String> = emptyMap()
    val text = StringBuilder()

    fun collected(): String = text.toString().collapseWhitespace()

    xmlEvents(packageXml).forEach { event ->
        when (event) {
            is XmlEvent.Open -> when {
                event.name == "metadata" -> inMetadata = true

                inMetadata && !event.selfClosing -> {
                    current = event.name
                    currentAttributes = event.attributes
                    text.clear()
                }
            }

            is XmlEvent.Text -> if (inMetadata && current != null) text.append(event.text)

            is XmlEvent.Close -> when {
                event.name == "metadata" -> inMetadata = false

                inMetadata && event.name == current -> {
                    val value = collected()

                    if (value.isNotBlank()) {
                        when (event.name) {
                            "title" -> if (title == null) title = value
                            "creator" -> authors += value
                            "publisher" -> if (publisher == null) publisher = value
                            "date" -> if (published == null) published = value
                            "language" -> if (language == null) language = value
                            "description" -> if (description == null) description = value.stripMarkup()
                            "subject" -> subjects += value

                            "identifier" -> {
                                // A book carries several - a UUID the tooling
                                // generated, sometimes an ISBN as well. The ISBN
                                // is the one a reader recognises, so it wins
                                // wherever the book declares which is which.
                                if (currentAttributes.declaresIsbn() || value.looksLikeIsbn()) {
                                    if (isbn == null) isbn = value.isbnDigits()
                                } else if (identifier == null) {
                                    identifier = value
                                }
                            }
                        }
                    }

                    current = null
                    currentAttributes = emptyMap()
                    text.clear()
                }
            }
        }
    }

    return BookMetadata(
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
