package io.lukia.features.reader.infrastructure.pdf

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
 *
 * @param baseOffset where [bytes] starts in the file, so stream data offsets
 *   come back absolute.
 */
internal class PdfObjectParser(
    internal val bytes: ByteArray,
    private val baseOffset: Long = 0L,
) {

    internal var position = 0

    /**
     * Parses `<number> <generation> obj ... endobj` at [offset], returning the
     * object's value - or a [PdfValue.Stream] when a stream follows it.
     */
    fun parseIndirectObject(offset: Int = 0): PdfValue? {
        position = offset

        readInteger() ?: return null
        readInteger() ?: return null
        if (!readKeyword("obj")) return null

        val value = parseValue() ?: return null

        skipWhitespaceAndComments()
        if (!readKeyword("stream")) return value

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

            val key = (parseName() as? PdfValue.Name)?.value ?: return null
            val value = parseValue() ?: return null
            entries[key] = value
        }
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

            if (byte == HASH && position + 2 < bytes.size) {
                val decoded = hexValue(bytes[position + 1], bytes[position + 2])
                if (decoded != null) {
                    name.append(decoded.toChar())
                    position += 3
                    continue
                }
            }

            name.append((byte.toInt() and 0xFF).toChar())
            position++
        }

        return PdfValue.Name(name.toString())
    }

    /**
     * A number, a reference, or one of the three keywords. References are only
     * recognisable by looking past two integers for an `R`, so the position is
     * put back when it turns out to be a plain number.
     */
    private fun parseKeywordOrNumber(): PdfValue? {
        if (readKeyword("true")) return PdfValue.Bool(true)
        if (readKeyword("false")) return PdfValue.Bool(false)
        if (readKeyword("null")) return PdfValue.Null

        val start = position
        val number = readNumber() ?: return null

        if (number == number.toInt().toDouble() && number >= 0) {
            val afterFirst = position
            val generation = readInteger()

            if (generation != null && readKeyword("R")) {
                return PdfValue.Reference(number.toInt(), generation)
            }

            position = afterFirst
        }

        if (position == start) return null

        return PdfValue.Number(number)
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

        if (position + keyword.length > bytes.size) return false

        for (index in keyword.indices) {
            if (bytes[position + index] != keyword[index].code.toByte()) return false
        }

        // Must not be the start of a longer token: "R" should not match "Rotate".
        val after = position + keyword.length
        if (after < bytes.size && !isWhitespace(bytes[after]) && !isDelimiter(bytes[after])) {
            return false
        }

        position = after
        return true
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
