package io.lukia.features.reader.infrastructure.pdf

/**
 * The subset of PDF's object model needed to find a table of contents: the
 * dictionaries, arrays, names, numbers, strings and indirect references that an
 * outline, a page tree and a name tree are built from.
 *
 * Streams are represented by their dictionary plus where their data starts;
 * nothing here decodes one.
 */
internal sealed interface PdfValue {

    data class Dictionary(val entries: Map<String, PdfValue>) : PdfValue {
        operator fun get(key: String): PdfValue? = entries[key]
    }

    data class Array(val items: List<PdfValue>) : PdfValue

    data class Name(val value: String) : PdfValue

    data class Number(val value: Double) : PdfValue

    /** Decoded to text; the bytes themselves are never needed here. */
    data class Text(val value: String) : PdfValue

    data class Reference(val objectNumber: Int, val generation: Int) : PdfValue

    data class Bool(val value: Boolean) : PdfValue

    data object Null : PdfValue

    /**
     * A stream's dictionary together with the absolute offset its data begins
     * at, so the data can be read later without holding it now.
     */
    data class Stream(
        val dictionary: Dictionary,
        val dataOffset: Long
    ) : PdfValue
}

internal val PdfValue?.asDictionary: PdfValue.Dictionary?
    get() = when (this) {
        is PdfValue.Dictionary -> this
        is PdfValue.Stream -> dictionary
        else -> null
    }

internal val PdfValue?.asArray: List<PdfValue>?
    get() = (this as? PdfValue.Array)?.items

internal val PdfValue?.asName: String?
    get() = (this as? PdfValue.Name)?.value

internal val PdfValue?.asInt: Int?
    get() = (this as? PdfValue.Number)?.value?.toInt()

internal val PdfValue?.asText: String?
    get() = (this as? PdfValue.Text)?.value
