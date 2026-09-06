package io.lukia.core.infrastructure.pdf

/**
 * Reads PDF objects out of a window of bytes.
 *
 * Deliberately forgiving: every parse returns null rather than throwing when the
 * bytes do not say what was expected. A reader that fails to find a chapter list
 * should show a book without chapters, never fail to open the book - and the
 * files this meets are produced by decades of tools of varying quality.
 *
 * Returning null also covers the window ending mid-object, which is how the
 * caller knows to read more of the file and try again.
 */
internal class PdfObjectParser(
    /**
     * The window to read, which need not begin at an object boundary -
     * [parseIndirectObject] is told where in it to start.
     */
    internal val bytes: ByteArray,
    /** Where [bytes] sits in the file, so a stream's offset comes back absolute. */
    private val baseOffset: Long = 0L,
) {

    internal var position = 0

    /**
     * Parses `<number> <generation> obj ... endobj` at [offset], returning the
     * object's value - or a [PdfValue.Stream] when a stream follows it.
     */
    fun parseIndirectObject(offset: Int = 0): PdfValue? {
        position = offset

        if (!skipObjectHeader()) return null

        val value = parseValue() ?: return null

        skipWhitespaceAndComments()

        return if (readKeyword("stream")) streamAfter(value) else value
    }

    /** Reads past `<number> <generation> obj`, false when that is not what is here. */
    private fun skipObjectHeader(): Boolean {
        readInteger() ?: return false
        readInteger() ?: return false

        return readKeyword("obj")
    }

    /**
     * The stream body that followed [value], or null when [value] was not a
     * dictionary - only a dictionary can say how long a stream is.
     */
    private fun streamAfter(value: PdfValue): PdfValue? {
        // The data begins after the newline that closes the `stream` keyword,
        // which the spec allows to be either CRLF or a bare LF.
        if (position < bytes.size && bytes[position] == CARRIAGE_RETURN) position++
        if (position < bytes.size && bytes[position] == LINE_FEED) position++

        val dictionary = value.asDictionary ?: return null

        return PdfValue.Stream(
            dictionary = dictionary,
            dataOffset = baseOffset + position,
        )
    }

    fun parseValue(): PdfValue? {
        skipWhitespaceAndComments()
        if (position >= bytes.size) return null

        return when (bytes[position]) {
            LESS_THAN -> if (peek(1) == LESS_THAN) parseDictionary() else parseHexString()
            OPEN_BRACKET -> parseArray()
            SLASH -> parseName()
            OPEN_PAREN -> parseLiteralString()
            else -> parseKeywordOrNumber()
        }
    }

    private fun parseDictionary(): PdfValue? {
        position += 2

        val entries = LinkedHashMap<String, PdfValue>()

        while (true) {
            skipWhitespaceAndComments()
            if (position >= bytes.size) return null

            if (bytes[position] == GREATER_THAN && peek(1) == GREATER_THAN) {
                position += 2
                return PdfValue.Dictionary(entries)
            }

            if (!readEntryInto(entries)) return null
        }
    }

    /** False for a pair the bytes do not spell, which makes the dictionary a loss. */
    private fun readEntryInto(entries: MutableMap<String, PdfValue>): Boolean {
        val key = (parseName() as? PdfValue.Name)?.value ?: return false
        val value = parseValue() ?: return false

        entries[key] = value
        return true
    }

    private fun parseArray(): PdfValue? {
        position++

        val items = mutableListOf<PdfValue>()

        while (true) {
            skipWhitespaceAndComments()
            if (position >= bytes.size) return null

            if (bytes[position] == CLOSE_BRACKET) {
                position++
                return PdfValue.Array(items)
            }

            items += parseValue() ?: return null
        }
    }

    private fun parseName(): PdfValue? {
        skipWhitespaceAndComments()
        if (position >= bytes.size || bytes[position] != SLASH) return null
        position++

        val name = StringBuilder()

        while (position < bytes.size) {
            val byte = bytes[position]
            if (isWhitespace(byte) || isDelimiter(byte)) break

            name.append(nextNameChar())
        }

        return PdfValue.Name(name.toString())
    }

    /**
     * One character of a name, advancing past what spelled it.
     *
     * `#XX` is how a name holds a character it cannot write plainly - a space, a
     * slash - and a `#` that is not followed by two hex digits is just a `#`.
     */
    private fun nextNameChar(): Char {
        val byte = bytes[position]

        if (byte == HASH && position + 2 < bytes.size) {
            val decoded = hexValue(bytes[position + 1], bytes[position + 2])

            if (decoded != null) {
                position += 3
                return decoded.toChar()
            }
        }

        position++
        return (byte.toInt() and 0xFF).toChar()
    }

    /** A number, a reference, or one of the three keywords. */
    private fun parseKeywordOrNumber(): PdfValue? {
        val keyword = PdfKeyword.entries.firstOrNull { readKeyword(it.text) }

        if (keyword != null) return keyword.value

        val start = position
        val number = readNumber() ?: return null

        return referenceFrom(number) ?: PdfValue.Number(number).takeIf { position != start }
    }

    /**
     * The reference [number] opens, or null when it opens nothing.
     *
     * References are only recognisable by looking past two integers for an `R`,
     * so the position is put back when it turns out to be a plain number.
     */
    private fun referenceFrom(number: Double): PdfValue.Reference? {
        if (number != number.toInt().toDouble() || number < 0) return null

        val afterFirst = position
        val generation = readInteger()

        if (generation != null && readKeyword("R")) {
            return PdfValue.Reference(number.toInt(), generation)
        }

        position = afterFirst
        return null
    }

    private fun readNumber(): Double? {
        skipWhitespaceAndComments()

        val start = position
        if (position < bytes.size && (bytes[position] == PLUS || bytes[position] == MINUS)) position++

        var sawDigit = false
        while (position < bytes.size) {
            val byte = bytes[position]
            if (byte in ZERO..NINE) {
                sawDigit = true
                position++
            } else if (byte == PERIOD) {
                position++
            } else {
                break
            }
        }

        if (!sawDigit) {
            position = start
            return null
        }

        return bytes.decodeToString(start, position).toDoubleOrNull()
            ?: run {
                position = start
                null
            }
    }

    private fun readInteger(): Int? {
        skipWhitespaceAndComments()

        val start = position
        while (position < bytes.size && bytes[position] in ZERO..NINE) position++

        if (position == start) return null

        return bytes.decodeToString(start, position).toIntOrNull()
            ?: run {
                position = start
                null
            }
    }

    /** Consumes [keyword] if it is next, leaving the position alone if not. */
    private fun readKeyword(keyword: String): Boolean {
        skipWhitespaceAndComments()

        val after = position + keyword.length

        if (!spells(keyword, endingAt = after)) return false

        position = after
        return true
    }

    /**
     * Whether the bytes from the current position spell [keyword] and then stop.
     *
     * The stopping is half the test: without it "R" matches the start of
     * "Rotate", and a page's rotation becomes a reference to object zero.
     */
    private fun spells(keyword: String, endingAt: Int): Boolean {
        if (endingAt > bytes.size) return false
        if (keyword.indices.any { bytes[position + it] != keyword[it].code.toByte() }) return false

        return endingAt == bytes.size ||
            isWhitespace(bytes[endingAt]) ||
            isDelimiter(bytes[endingAt])
    }

    private fun skipWhitespaceAndComments() {
        while (position < bytes.size) {
            val byte = bytes[position]

            when {
                isWhitespace(byte) -> position++

                byte == PERCENT -> {
                    while (position < bytes.size &&
                        bytes[position] != LINE_FEED &&
                        bytes[position] != CARRIAGE_RETURN
                    ) {
                        position++
                    }
                }

                else -> return
            }
        }
    }

    private fun peek(ahead: Int): Byte? = bytes.getOrNull(position + ahead)
}

/** The three values a PDF writes as a bare word rather than as bytes. */
private enum class PdfKeyword(val text: String, val value: PdfValue) {
    True("true", PdfValue.Bool(true)),
    False("false", PdfValue.Bool(false)),
    Null("null", PdfValue.Null),
}
