package io.lukia.core.infrastructure.pdf

/**
 * PDF text is either UTF-16 with a byte-order mark, or PDFDocEncoding. Both
 * appear in the wild; the mark is what separates them.
 */
fun decodeText(raw: ByteArray): String {
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
