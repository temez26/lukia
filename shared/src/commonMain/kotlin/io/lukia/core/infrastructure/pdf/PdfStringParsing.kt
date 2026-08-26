package io.lukia.core.infrastructure.pdf

// The two shapes a PDF string object takes - `(literal)` and `<48657820>` - and
// the escapes inside them. Split from the structural parser because escape
// handling is the fiddliest part of the syntax and the least like the rest.

internal fun PdfObjectParser.parseLiteralString(): PdfValue? {
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

internal fun PdfObjectParser.parseHexString(): PdfValue? {
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
