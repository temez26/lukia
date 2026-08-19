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
    private val bytes: ByteArray,
    private val baseOffset: Long = 0L,
) {

    private var position = 0

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

    private fun parseLiteralString(): PdfValue? {
        position++

        val raw = mutableListOf<Byte>()
        var depth = 1

        while (position < bytes.size) {
            val byte = bytes[position]

            when {
                byte == BACKSLASH -> {
                    position++
                    if (position >= bytes.size) return null
                    raw.addAll(readEscapedBytes())
                    continue
                }

                byte == OPEN_PAREN -> {
                    depth++
                    raw.add(byte)
                }

                byte == CLOSE_PAREN -> {
                    depth--
                    if (depth == 0) {
                        position++
                        return PdfValue.Text(decodeText(raw.toByteArray()))
                    }
                    raw.add(byte)
                }

                else -> raw.add(byte)
            }

            position++
        }

        return null
    }

    /** Handles the escapes a title can realistically contain. */
    private fun readEscapedBytes(): List<Byte> {
        val escape = bytes[position]
        position++

        return when (escape) {
            'n'.code.toByte() -> listOf(LINE_FEED)
            'r'.code.toByte() -> listOf(CARRIAGE_RETURN)
            't'.code.toByte() -> listOf('\t'.code.toByte())
            'b'.code.toByte() -> listOf('\b'.code.toByte())
            'f'.code.toByte() -> listOf(0x0C)
            LINE_FEED -> emptyList()
            CARRIAGE_RETURN -> {
                if (position < bytes.size && bytes[position] == LINE_FEED) position++
                emptyList()
            }

            in '0'.code.toByte()..'7'.code.toByte() -> {
                var value = escape - '0'.code.toByte()
                var digits = 1
                while (digits < 3 &&
                    position < bytes.size &&
                    bytes[position] >= '0'.code.toByte() &&
                    bytes[position] <= '7'.code.toByte()
                ) {
                    value = value * 8 + (bytes[position] - '0'.code.toByte())
                    position++
                    digits++
                }
                listOf(value.toByte())
            }

            else -> listOf(escape)
        }
    }

    private fun parseHexString(): PdfValue? {
        position++

        val raw = mutableListOf<Byte>()
        var high: Int? = null

        while (position < bytes.size) {
            val byte = bytes[position]
            position++

            if (byte == GREATER_THAN) {
                // An odd number of digits is padded with a trailing zero.
                if (high != null) raw.add((high shl 4).toByte())
                return PdfValue.Text(decodeText(raw.toByteArray()))
            }

            if (isWhitespace(byte)) continue

            val digit = hexDigit(byte) ?: return null
            if (high == null) {
                high = digit
            } else {
                raw.add(((high shl 4) or digit).toByte())
                high = null
            }
        }

        return null
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

    private companion object {
        const val LINE_FEED: Byte = 0x0A
        const val CARRIAGE_RETURN: Byte = 0x0D
        const val SLASH: Byte = 0x2F
        const val PERCENT: Byte = 0x25
        const val HASH: Byte = 0x23
        const val BACKSLASH: Byte = 0x5C
        const val OPEN_PAREN: Byte = 0x28
        const val CLOSE_PAREN: Byte = 0x29
        const val LESS_THAN: Byte = 0x3C
        const val GREATER_THAN: Byte = 0x3E
        const val OPEN_BRACKET: Byte = 0x5B
        const val CLOSE_BRACKET: Byte = 0x5D
        const val PLUS: Byte = 0x2B
        const val MINUS: Byte = 0x2D
        const val PERIOD: Byte = 0x2E
        const val ZERO: Byte = 0x30
        const val NINE: Byte = 0x39
    }
}

internal fun isWhitespace(byte: Byte): Boolean =
    byte == 0x00.toByte() ||
        byte == 0x09.toByte() ||
        byte == 0x0A.toByte() ||
        byte == 0x0C.toByte() ||
        byte == 0x0D.toByte() ||
        byte == 0x20.toByte()

internal fun isDelimiter(byte: Byte): Boolean =
    byte == 0x28.toByte() ||
        byte == 0x29.toByte() ||
        byte == 0x3C.toByte() ||
        byte == 0x3E.toByte() ||
        byte == 0x5B.toByte() ||
        byte == 0x5D.toByte() ||
        byte == 0x7B.toByte() ||
        byte == 0x7D.toByte() ||
        byte == 0x2F.toByte() ||
        byte == 0x25.toByte()

private fun hexDigit(byte: Byte): Int? = when (byte) {
    in 0x30.toByte()..0x39.toByte() -> byte - 0x30
    in 0x41.toByte()..0x46.toByte() -> byte - 0x41 + 10
    in 0x61.toByte()..0x66.toByte() -> byte - 0x61 + 10
    else -> null
}

private fun hexValue(high: Byte, low: Byte): Int? {
    val highDigit = hexDigit(high) ?: return null
    val lowDigit = hexDigit(low) ?: return null
    return (highDigit shl 4) or lowDigit
}

/**
 * PDF text is either UTF-16 with a byte-order mark, or PDFDocEncoding. Both
 * appear in the wild; the mark is what separates them.
 */
private fun decodeText(raw: ByteArray): String {
    if (raw.size >= 2 && raw[0] == 0xFE.toByte() && raw[1] == 0xFF.toByte()) {
        return buildString {
            var index = 2
            while (index + 1 < raw.size) {
                val code = ((raw[index].toInt() and 0xFF) shl 8) or (raw[index + 1].toInt() and 0xFF)
                append(code.toChar())
                index += 2
            }
        }
    }

    return buildString {
        raw.forEach { byte -> append(pdfDocEncoded(byte.toInt() and 0xFF)) }
    }
}

/**
 * PDFDocEncoding is Latin-1 apart from two stretches, and those stretches are
 * exactly where a book's punctuation lives: reading them as Latin-1 turns an
 * em dash in a chapter title into a stray quotation mark.
 */
private fun pdfDocEncoded(code: Int): Char = when (code) {
    in 0x18..0x1F -> PDF_DOC_DIACRITICS[code - 0x18]
    in 0x80..0xA0 -> PDF_DOC_PUNCTUATION[code - 0x80]
    else -> code.toChar()
}

private val PDF_DOC_DIACRITICS = charArrayOf(
    '˘',
    'ˇ',
    'ˆ',
    '˙',
    '˝',
    '˛',
    '˚',
    '˜',
)

private val PDF_DOC_PUNCTUATION = charArrayOf(
    '•', '†', '‡', '…', '—', '–', 'ƒ', '⁄',
    '‹', '›', '−', '‰', '„', '“', '”', '‘',
    '’', '‚', '™', 'ﬁ', 'ﬂ', 'Ł', 'Œ', 'Š',
    'Ÿ', 'Ž', 'ı', 'ł', 'œ', 'š', 'ž', '�',
    '€',
)
