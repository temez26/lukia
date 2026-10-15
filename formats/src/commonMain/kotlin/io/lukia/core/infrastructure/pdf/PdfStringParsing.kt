package io.lukia.core.infrastructure.pdf

// The two shapes a PDF string object takes - `(literal)` and `<48657820>` - and
// the escapes inside them. Split from the structural parser because escape
// handling is the fiddliest part of the syntax and the least like the rest.

fun PdfObjectParser.parseLiteralString(): PdfValue? {
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
private fun PdfObjectParser.readEscapedBytes(): List<Byte> {
    val escape = bytes[position]
    position++

    return when (escape) {
        'n'.code.toByte() -> listOf(LINE_FEED)
        'r'.code.toByte() -> listOf(CARRIAGE_RETURN)
        't'.code.toByte() -> listOf('\t'.code.toByte())
        'b'.code.toByte() -> listOf('\b'.code.toByte())
        'f'.code.toByte() -> listOf(FORM_FEED)
        LINE_FEED -> emptyList()
        CARRIAGE_RETURN -> {
            if (position < bytes.size && bytes[position] == LINE_FEED) position++
            emptyList()
        }

        in '0'.code.toByte()..'7'.code.toByte() -> listOf(readOctalByte(escape))

        else -> listOf(escape)
    }
}

/**
 * `\101` is `A`: up to three octal digits, of which [first] has already been
 * read. Fewer than three is legal - the escape ends at the first byte that is
 * not an octal digit, which is how `\0` is written next to a `9`.
 */
private fun PdfObjectParser.readOctalByte(first: Byte): Byte {
    var value = first - '0'.code.toByte()
    var digits = 1

    while (digits < OctalDigits && position < bytes.size && bytes[position].isOctalDigit()) {
        value = value * OctalBase + (bytes[position] - '0'.code.toByte())
        position++
        digits++
    }

    return value.toByte()
}

private fun Byte.isOctalDigit(): Boolean =
    this >= '0'.code.toByte() && this <= '7'.code.toByte()

private const val OctalDigits = 3
private const val OctalBase = 8

fun PdfObjectParser.parseHexString(): PdfValue? {
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
