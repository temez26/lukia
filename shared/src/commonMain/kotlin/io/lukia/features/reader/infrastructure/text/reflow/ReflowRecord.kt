package io.lukia.features.reader.infrastructure.text.reflow

import io.lukia.features.reader.domain.ReflowableContent
import io.lukia.features.reader.domain.outline.OutlineEntry
import io.lukia.features.reader.domain.text.BlockAlignment
import io.lukia.features.reader.domain.text.Emphasis
import io.lukia.features.reader.domain.text.TextBlock
import io.lukia.features.reader.domain.text.TextRun
import io.lukia.features.reader.domain.text.TextSection
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A converted book written down, so that converting it was work done once.
 *
 * Two shapes of thing, kept apart. The words and structure are small and awkward
 * - nested, optional, seven kinds of block - which is what a serialiser is for.
 * The pictures are neither: already-encoded images, sometimes tens of megabytes,
 * which through JSON would spell every byte as four characters of text.
 *
 * So the record is a frame: a small header, the structure as JSON with each
 * picture named by the offset it will be found at, then the pictures laid end to
 * end exactly as they came out of the file.
 *
 * Not a public format - this app's own note to itself about a file it has read.
 * A note it cannot make sense of is thrown away and made again, which is what
 * [MAGIC] and [VERSION] are for and what makes changing any of this safe.
 */
private const val MAGIC = "LUKIATEXT"

/** Raise this whenever what is written here stops meaning what it used to. */
private const val VERSION = 1

private val format = Json { ignoreUnknownKeys = true }

/**
 * This book as bytes, tied to the size of the file it was read out of.
 *
 * [sourceSize] is the whole check that the note still describes the book, and
 * what it measures matters: the working copy in app storage, not the imported
 * file. That copy is what both ways of reading a book read, so a note matching
 * it describes the same book the pages would show - and a copy remade from a
 * changed original comes back a different length.
 *
 * A length rather than a hash, because hashing means reading hundreds of
 * megabytes of PDF on the way into every book - the cost this file exists to
 * avoid. What slips through is a replacement of exactly the same length, which
 * would also leave the drawn pages showing the old book, so the two stay in
 * step either way.
 */
internal fun ReflowableContent.asRecord(sourceSize: Long): ByteArray {
    val figures = mutableListOf<ByteArray>()

    val book = StoredBook(
        sections = sections.map { section ->
            StoredSection(
                title = section.title,
                blocks = section.blocks.map { it.stored(figures) },
            )
        },
        outline = outline?.map { it.stored() },
    )

    return frame(format.encodeToString(book).encodeToByteArray(), sourceSize, figures)
}

/**
 * The book these bytes hold, or null when they are not a note this version of
 * the app wrote about a file of exactly this size.
 *
 * Null covers every way of being wrong at once - a record from an older
 * version, one for a book that has since been replaced, one cut short by the
 * app being killed mid-write - because the caller does the same thing about all
 * of them, which is to read the document again.
 */
internal fun readRecord(bytes: ByteArray, sourceSize: Long): ReflowableContent? {
    val frames = Frames(bytes).openedFor(sourceSize) ?: return null

    return frames.book()?.let { book -> frames.figures()?.let(book::asContent) }
}

/**
 * These bytes with the header read off, or null unless the header says they are
 * a note this version of the app wrote about a file of exactly this size.
 */
private fun Frames.openedFor(sourceSize: Long): Frames? {
    if (!opensWith(MAGIC)) return null
    if (int() != VERSION) return null
    if (long() != sourceSize) return null

    return this
}

private fun Frames.book(): StoredBook? = block()?.let { json ->
    runCatching { format.decodeFromString<StoredBook>(json.decodeToString()) }.getOrNull()
}

/** Null the moment one promised picture is not there, which is a short record. */
private fun Frames.figures(): List<ByteArray>? {
    val count = int() ?: return null

    return buildList { repeat(count) { add(block() ?: return null) } }
}

private fun StoredBook.asContent(figures: List<ByteArray>): ReflowableContent = ReflowableContent(
    sections = sections.map { section ->
        TextSection(
            title = section.title,
            blocks = section.blocks.mapNotNull { it.asBlock(figures) },
        )
    },
    outline = outline?.map { it.asEntry() },
)

private fun frame(book: ByteArray, sourceSize: Long, figures: List<ByteArray>): ByteArray {
    val header = MAGIC.encodeToByteArray()
    val size = header.size + INT_BYTES + LONG_BYTES + INT_BYTES + book.size +
        INT_BYTES + figures.sumOf { INT_BYTES + it.size }

    val record = ByteArray(size)

    header.copyInto(record)

    var at = record.putInt(header.size, VERSION)
    at = record.putLong(at, sourceSize)
    at = record.putBlock(at, book)
    at = record.putInt(at, figures.size)

    figures.forEach { at = record.putBlock(at, it) }

    return record
}

private const val INT_BYTES = 4
private const val LONG_BYTES = 8
private const val BYTE_BITS = 8
private const val BYTE_MASK = 0xFF

private fun ByteArray.putInt(at: Int, value: Int): Int {
    for (step in 0 until INT_BYTES) {
        this[at + step] = (value shr ((INT_BYTES - 1 - step) * BYTE_BITS)).toByte()
    }

    return at + INT_BYTES
}

private fun ByteArray.putLong(at: Int, value: Long): Int {
    for (step in 0 until LONG_BYTES) {
        this[at + step] = (value shr ((LONG_BYTES - 1 - step) * BYTE_BITS)).toByte()
    }

    return at + LONG_BYTES
}

private fun ByteArray.putBlock(at: Int, block: ByteArray): Int {
    val start = putInt(at, block.size)
    block.copyInto(this, start)

    return start + block.size
}

/**
 * A record being read, one piece at a time, where running out of bytes is an
 * answer rather than a crash.
 *
 * Every length in the file was written by this app and every one of them is
 * still treated as a claim: a record truncated by a write that never finished
 * states a length longer than what follows it, and that has to come back as a
 * record this cannot read rather than as an index out of bounds.
 */
private class Frames(private val bytes: ByteArray) {

    private var at = 0

    fun opensWith(text: String): Boolean {
        val expected = text.encodeToByteArray()

        if (bytes.size < expected.size) return false
        if (!bytes.copyOfRange(0, expected.size).contentEquals(expected)) return false

        at = expected.size

        return true
    }

    fun int(): Int? {
        if (at + INT_BYTES > bytes.size) return null

        var value = 0
        repeat(INT_BYTES) { value = (value shl BYTE_BITS) or (bytes[at++].toInt() and BYTE_MASK) }

        return value
    }

    fun long(): Long? {
        if (at + LONG_BYTES > bytes.size) return null

        var value = 0L
        repeat(LONG_BYTES) {
            value = (value shl BYTE_BITS) or (bytes[at++].toLong() and BYTE_MASK.toLong())
        }

        return value
    }

    fun block(): ByteArray? {
        val length = int() ?: return null
        if (length < 0 || at + length > bytes.size) return null

        val block = bytes.copyOfRange(at, at + length)
        at += length

        return block
    }
}

@Serializable
private class StoredBook(val sections: List<StoredSection>, val outline: List<StoredEntry>?)

@Serializable
private class StoredSection(val title: String? = null, val blocks: List<StoredBlock>)

@Serializable
private class StoredEntry(
    val title: String,
    val page: Int,
    val level: Int,
    val block: Int = 0,
)

@Serializable
private class StoredRun(val text: String, val marks: List<String> = emptyList())

/**
 * The blocks as they are written down.
 *
 * A mirror of [TextBlock] rather than the thing itself, because what a book is
 * made of and how this app happens to write it down are two separate questions -
 * and the day one of them changes should not be the day the other has to.
 */
@Serializable
private sealed interface StoredBlock

@Serializable
@SerialName("heading")
private class StoredHeading(
    val text: List<StoredRun>,
    val level: Int,
    val align: String? = null,
) : StoredBlock

@Serializable
@SerialName("paragraph")
private class StoredParagraph(val text: List<StoredRun>, val align: String? = null) : StoredBlock

@Serializable
@SerialName("item")
private class StoredItem(
    val text: List<StoredRun>,
    val marker: String,
    val align: String? = null,
) : StoredBlock

@Serializable
@SerialName("quote")
private class StoredQuote(val text: List<StoredRun>, val align: String? = null) : StoredBlock

@Serializable
@SerialName("code")
private class StoredCode(val text: String) : StoredBlock

/** [at] is where in the pictures laid after the structure this one begins. */
@Serializable
@SerialName("figure")
private class StoredFigure(val at: Int, val description: String? = null) : StoredBlock

@Serializable
@SerialName("rule")
private data object StoredRule : StoredBlock

private fun TextBlock.stored(figures: MutableList<ByteArray>): StoredBlock = when (this) {
    is TextBlock.Heading -> StoredHeading(text.stored(), level, alignment?.name)
    is TextBlock.Paragraph -> StoredParagraph(text.stored(), alignment?.name)
    is TextBlock.ListItem -> StoredItem(text.stored(), marker, alignment?.name)
    is TextBlock.Quote -> StoredQuote(text.stored(), alignment?.name)
    is TextBlock.Code -> StoredCode(text)
    is TextBlock.Separator -> StoredRule
    is TextBlock.Figure -> StoredFigure(at = figures.size, description = description)
        .also { figures.add(bytes) }
}

/**
 * Null only for a figure pointing past the pictures that were read, which a
 * record written by [asRecord] never does - the framing refuses a short record
 * before this is reached. It is here so that a block whose picture cannot be
 * found costs that block rather than an exception, whatever future version of
 * this writes the note.
 */
private fun StoredBlock.asBlock(figures: List<ByteArray>): TextBlock? = when (this) {
    is StoredHeading -> TextBlock.Heading(text.asRuns(), level, align.asAlignment())
    is StoredParagraph -> TextBlock.Paragraph(text.asRuns(), align.asAlignment())
    is StoredItem -> TextBlock.ListItem(text.asRuns(), marker, align.asAlignment())
    is StoredQuote -> TextBlock.Quote(text.asRuns(), align.asAlignment())
    is StoredCode -> TextBlock.Code(text)
    is StoredRule -> TextBlock.Separator
    is StoredFigure -> figures.getOrNull(at)?.let { TextBlock.Figure(it, description) }
}

private fun List<TextRun>.stored(): List<StoredRun> =
    map { run -> StoredRun(run.text, run.emphasis.map { it.name }) }

private fun List<StoredRun>.asRuns(): List<TextRun> =
    map { run -> TextRun(run.text, run.marks.asEmphasis()) }

private fun OutlineEntry.stored(): StoredEntry =
    StoredEntry(title = title, page = pageIndex, level = level, block = blockIndex)

private fun StoredEntry.asEntry(): OutlineEntry =
    OutlineEntry(title = title, pageIndex = page, level = level, blockIndex = block)

/**
 * Read by name, and quietly dropped where the name is not one this app knows.
 *
 * A record written by a later version can hold an alignment or a mark this one
 * has never heard of. Refusing the whole book over it would throw away a
 * conversion for the sake of one paragraph's centring.
 */
private fun String?.asAlignment(): BlockAlignment? =
    BlockAlignment.entries.firstOrNull { it.name == this }

private fun List<String>.asEmphasis(): Set<Emphasis> =
    mapNotNull { mark -> Emphasis.entries.firstOrNull { it.name == mark } }.toSet()
