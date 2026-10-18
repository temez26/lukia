package io.lukia.core.infrastructure.xml

/**
 * One thing found while walking a document: a tag opening, a tag closing, or the
 * text between them.
 *
 * Deliberately a flat stream rather than a tree. Everything read here is turned
 * straight into something else - an EPUB chapter into paragraphs, a DOCX into
 * runs of text - and building a document tree first would mean holding a second
 * whole copy of the book in memory to throw away immediately afterwards.
 */
sealed interface XmlEvent {

    data class Open(
        /** Lower-cased and with any namespace prefix removed: `w:p` arrives as `p`. */
        val name: String,
        val attributes: Map<String, String>,
        /** True for `<br/>`, which has no [Close] of its own. */
        val selfClosing: Boolean,
    ) : XmlEvent

    data class Close(val name: String) : XmlEvent

    data class Text(val text: String) : XmlEvent
}

/**
 * Walks [source] as a stream of [XmlEvent]s.
 *
 * A hand-written scanner rather than a parser dependency, for the same reason
 * the PDF outline is read by hand: the shapes needed are small and well
 * understood, and every platform would otherwise need its own XML library bound
 * behind an expect/actual.
 *
 * Forgiving by design, because it is fed XHTML from EPUBs produced by every tool
 * that has ever written one: an unclosed `<img>`, a stray `<`, a DOCTYPE or a
 * comment are skipped rather than failed on - a chapter that renders imperfectly
 * beats one that refuses to open.
 *
 * Lazy: callers stop as soon as they have what they came for, and a nav document
 * is read for its first list.
 */
fun xmlEvents(source: String): Sequence<XmlEvent> = sequence {
    var index = 0

    while (index < source.length) {
        val tagStart = source.indexOf('<', index)

        if (tagStart < 0) {
            emitText(source, index, source.length)
            return@sequence
        }

        emitText(source, index, tagStart)

        // A `<` that begins nothing tag-shaped is content someone failed to
        // escape, so it is taken as the text it plainly is.
        val skipTo = skipNonTag(source, tagStart)
        if (skipTo != null) {
            index = skipTo
            continue
        }

        val tagEnd = findTagEnd(source, tagStart)
        if (tagEnd < 0) {
            yield(XmlEvent.Text(source.substring(tagStart)))
            return@sequence
        }

        parseTag(source.substring(tagStart + 1, tagEnd))?.let { yield(it) }
        index = tagEnd + 1
    }
}

private suspend fun SequenceScope<XmlEvent>.emitText(source: String, from: Int, to: Int) {
    if (to <= from) return

    val text = decodeEntities(source.substring(from, to))
    if (text.isNotEmpty()) yield(XmlEvent.Text(text))
}

/**
 * Past a comment, CDATA section, DOCTYPE or processing instruction, or null when
 * [start] is none of those.
 *
 * CDATA is the odd one out: its content is text rather than markup, and it is
 * where an EPUB keeps a stylesheet or a script - both of which would otherwise
 * be read as prose and shown to the reader.
 */
private fun skipNonTag(source: String, start: Int): Int? = when {
    source.startsWith("<!--", start) ->
        endOf(source, start, "-->")

    source.startsWith("<![CDATA[", start) ->
        endOf(source, start, "]]>")

    source.startsWith("<!", start) || source.startsWith("<?", start) ->
        endOf(source, start, ">")

    // A `<` with no name after it cannot open a tag.
    start + 1 >= source.length || (!isNameStart(source[start + 1]) && source[start + 1] != '/') ->
        minOf(start + 1, source.length)

    else -> null
}

private fun endOf(source: String, start: Int, terminator: String): Int {
    val end = source.indexOf(terminator, start)
    return if (end < 0) source.length else end + terminator.length
}

/**
 * The `>` that closes the tag opening at [start], skipping any inside a quoted
 * attribute value - `alt="a > b"` is one attribute, not the end of the tag.
 */
private fun findTagEnd(source: String, start: Int): Int {
    var quote: Char? = null
    var index = start + 1

    while (index < source.length) {
        val char = source[index]

        when {
            quote != null -> if (char == quote) quote = null
            char == '"' || char == '\'' -> quote = char
            char == '>' -> return index
        }

        index++
    }

    return -1
}

private fun parseTag(body: String): XmlEvent? {
    if (body.isEmpty()) return null

    if (body.startsWith('/')) {
        val name = localName(body.drop(1).trim())
        return if (name.isEmpty()) null else XmlEvent.Close(name)
    }

    val selfClosing = body.endsWith('/')
    val content = if (selfClosing) body.dropLast(1) else body

    val nameEnd = content.indexOfFirst { it.isWhitespace() }
    val rawName = if (nameEnd < 0) content else content.substring(0, nameEnd)
    val name = localName(rawName)

    if (name.isEmpty()) return null

    val attributes = if (nameEnd < 0) {
        emptyMap()
    } else {
        parseAttributes(content.substring(nameEnd + 1))
    }

    return XmlEvent.Open(name = name, attributes = attributes, selfClosing = selfClosing)
}

private val AttributePattern = Regex(
    """([\w:.\-]+)\s*(?:=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'>]+)))?""",
)

/**
 * Attribute names keep their prefix where it carries meaning - `epub:type` is
 * what marks an EPUB 3 navigation list, and dropping the prefix would make it
 * indistinguishable from the `type` on an ordinary element.
 */
private fun parseAttributes(source: String): Map<String, String> = buildMap {
    AttributePattern.findAll(source).forEach { match ->
        val name = match.groupValues[1].lowercase()
        val value = match.groupValues.drop(2).firstOrNull { it.isNotEmpty() } ?: ""

        // First wins: a tag that repeats an attribute is malformed, and the
        // leading value is the one every browser keeps.
        if (name.isNotEmpty() && name !in this) put(name, decodeEntities(value))
    }
}

private fun localName(raw: String): String = raw.substringAfterLast(':').lowercase()

private fun isNameStart(char: Char): Boolean = char.isLetter() || char == '_'

/**
 * The handful of named entities that turn up in prose, plus every numeric one.
 *
 * Not the full HTML set on purpose: that is some two thousand names, nearly all
 * of which no book has ever used, and the numeric forms below already cover
 * anything a producer reaches for beyond this. An unrecognised name is left
 * exactly as written rather than dropped, so a stray `&` in the text survives as
 * itself.
 */
private val NamedEntities = mapOf(
    "amp" to "&",
    "lt" to "<",
    "gt" to ">",
    "quot" to "\"",
    "apos" to "'",
    "nbsp" to " ",
    "shy" to "­",
    "ndash" to "–",
    "mdash" to "—",
    "lsquo" to "‘",
    "rsquo" to "’",
    "ldquo" to "“",
    "rdquo" to "”",
    "hellip" to "…",
    "bull" to "•",
    "middot" to "·",
    "laquo" to "«",
    "raquo" to "»",
    "copy" to "©",
    "reg" to "®",
    "trade" to "™",
    "deg" to "°",
    "times" to "×",
    "eacute" to "é",
    "egrave" to "è",
    "agrave" to "à",
    "uuml" to "ü",
    "ouml" to "ö",
    "auml" to "ä",
    "szlig" to "ß",
)

private val EntityPattern = Regex("""&(#[Xx]?[0-9A-Fa-f]+|\w+);""")

/**
 * Character references in [source] as the characters they stand for.
 *
 * Public for text that was escaped twice by the tool that wrote it - a contents
 * label written as `&amp;#160;Foreword` arrives from [xmlEvents] still holding
 * `&#160;`, and decoding it once more is the only reading a person would expect.
 */
fun decodeEntities(source: String): String {
    if ('&' !in source) return source

    return EntityPattern.replace(source) { match ->
        val body = match.groupValues[1]

        when {
            body.startsWith("#x", ignoreCase = true) ->
                codePointOrNull(body.drop(2), radix = 16) ?: match.value

            body.startsWith("#") ->
                codePointOrNull(body.drop(1), radix = 10) ?: match.value

            else -> NamedEntities[body.lowercase()] ?: match.value
        }
    }
}

private fun codePointOrNull(digits: String, radix: Int): String? {
    val code = digits.toIntOrNull(radix) ?: return null

    // Past the last code point, or in the surrogate range where a lone half is
    // not a character at all.
    if (code !in 1..0x10FFFF || code in 0xD800..0xDFFF) return null

    return if (code <= 0xFFFF) {
        code.toChar().toString()
    } else {
        val offset = code - 0x10000
        charArrayOf(
            (0xD800 + (offset shr 10)).toChar(),
            (0xDC00 + (offset and 0x3FF)).toChar(),
        ).concatToString()
    }
}
