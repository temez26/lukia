package io.lukia.features.reader.infrastructure.text.html

import io.lukia.core.infrastructure.xml.xmlEvents
import io.lukia.features.reader.domain.text.TextBlock

/**
 * A chapter of XHTML read into the blocks the reader draws, and the places
 * inside it that can be linked to.
 *
 * [anchors] maps an element's `id` to the block it sits at, which is what lets a
 * table of contents pointing at `chapter.xhtml#part-two` land somewhere other
 * than the top of the file. Books that hold every chapter in one file address
 * their chapters this way and no other, so without it their contents panel is
 * a row per chapter that all lead to the same place.
 */
internal class HtmlContent(
    val blocks: List<TextBlock>,
    val anchors: Map<String, Int>,
)

/**
 * Turns a chapter of XHTML into the blocks the reader draws.
 *
 * This is where an EPUB stops being a web page and becomes a book. Deliberately
 * lossy: an EPUB may carry any HTML at all, with stylesheets that position,
 * colour and size every word, and honouring that faithfully means writing a
 * browser. What is kept is the structure a book is made of - headings,
 * paragraphs, lists, quotations, emphasis, illustrations - which is also what
 * survives being re-set at the reader's chosen size, and what a stylesheet
 * cannot be allowed to override if the type size setting is to mean anything.
 *
 * [image] is asked for an illustration's bytes by the `src` written in the file;
 * returning null leaves that figure out, which is what happens for a book that
 * references an image it does not contain.
 */
internal fun htmlContent(
    html: String,
    image: (src: String) -> ByteArray? = { null },
): HtmlContent {
    val builder = HtmlBlockBuilder(image)

    xmlEvents(html).forEach(builder::accept)

    return builder.finish()
}

/**
 * HTML treats any run of spaces, tabs and newlines as one space, and a file
 * whose paragraphs are wrapped at eighty columns is otherwise read as text full
 * of line breaks.
 */
internal fun String.collapseWhitespace(): String =
    buildString(length) {
        var lastWasSpace = false

        this@collapseWhitespace.forEach { char ->
            if (char.isWhitespace()) {
                if (!lastWasSpace) append(' ')
                lastWasSpace = true
            } else {
                append(char)
                lastWasSpace = false
            }
        }
    }
