package io.lukia.core.infrastructure.pdf

/**
 * A complete, valid PDF written by hand, with three pages and a two-entry
 * outline.
 *
 * Hand-written because the platforms that need testing do not all have a PDF
 * library to generate one with: the desktop tests can lean on PDFBox, an Android
 * device test cannot. This is the same bytes on both, which is what makes the
 * two sets of results comparable.
 *
 * The cross-reference table is written with real offsets, so this opens in a
 * platform PDF renderer as well as in the reader's own parser.
 */
object MinimalPdf {

    const val FIRST_CHAPTER_TITLE = "Introduction"
    const val SECOND_CHAPTER_TITLE = "The End"
    const val SECOND_CHAPTER_PAGE_INDEX = 2
    const val PAGE_COUNT = 3

    fun withOutline(): ByteArray {
        val objects = listOf(
            "<< /Type /Catalog /Pages 2 0 R /Outlines 6 0 R >>",
            "<< /Type /Pages /Kids [3 0 R 4 0 R 5 0 R] /Count 3 >>",
            page(contentsObject = 9),
            page(contentsObject = 10),
            page(contentsObject = 11),
            "<< /Type /Outlines /First 7 0 R /Last 8 0 R /Count 2 >>",
            "<< /Title ($FIRST_CHAPTER_TITLE) /Parent 6 0 R /Next 8 0 R /Dest [3 0 R /Fit] >>",
            "<< /Title ($SECOND_CHAPTER_TITLE) /Parent 6 0 R /Prev 7 0 R /Dest [5 0 R /Fit] >>",
            emptyContentStream(),
            emptyContentStream(),
            emptyContentStream(),
        )

        return assemble(objects, extraTrailerEntries = "")
    }

    /**
     * The same document with a document information dictionary, which is where a
     * PDF keeps what it knows about itself.
     *
     * [entries] is written into the dictionary verbatim, so a test can hand it
     * whatever a producer would - including the file names and application
     * banners that real files put in `/Title`.
     *
     * [catalogEntries] goes into the catalog instead, for the things PDF keeps
     * about the document rather than about the file - `/Lang` is the one this
     * app reads.
     */
    fun withInfo(entries: String, catalogEntries: String = ""): ByteArray {
        val objects = listOf(
            "<< /Type /Catalog /Pages 2 0 R $catalogEntries>>",
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            page(contentsObject = 4),
            emptyContentStream(),
            "<< $entries >>",
        )

        return assemble(objects, extraTrailerEntries = " /Info 5 0 R")
    }

    /**
     * A document with a catalog and no information dictionary at all, which is
     * an ordinary shape for a PDF and still says what language it is in.
     */
    fun withCatalog(catalogEntries: String): ByteArray {
        val objects = listOf(
            "<< /Type /Catalog /Pages 2 0 R $catalogEntries>>",
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            page(contentsObject = 4),
            emptyContentStream(),
        )

        return assemble(objects, extraTrailerEntries = "")
    }

    /**
     * The same three pages, with the catalog and the whole outline packed into
     * an object stream instead of written into the file directly.
     *
     * What PDF 1.5 and later writes, which is nearly everything produced since
     * about 2005: small objects gathered into one stream, only the streams
     * themselves addressable. Nothing else here reaches that path - [withOutline]
     * and the desktop's PDFBox fixtures both write 1.4 - so without this the
     * whole of `expandObjectStream` goes unexercised.
     *
     * The stream carries no filter, which is legal and is the only way to write
     * one from common code: there is a zlib decompressor here and no compressor.
     */
    fun withObjectStreams(): ByteArray {
        val packed = listOf(
            1 to "<< /Type /Catalog /Pages 2 0 R /Outlines 6 0 R >>",
            6 to "<< /Type /Outlines /First 7 0 R /Last 8 0 R /Count 2 >>",
            7 to "<< /Title ($FIRST_CHAPTER_TITLE) /Parent 6 0 R /Next 8 0 R /Dest [3 0 R /Fit] >>",
            8 to "<< /Title ($SECOND_CHAPTER_TITLE) /Parent 6 0 R /Prev 7 0 R /Dest [5 0 R /Fit] >>",
        )

        val objects = listOf(
            // Object one lives in the stream, so its slot here is a placeholder
            // that the scan must not prefer over the packed one.
            null,
            "<< /Type /Pages /Kids [3 0 R 4 0 R 5 0 R] /Count 3 >>",
            page(contentsObject = 9),
            page(contentsObject = 10),
            page(contentsObject = 11),
            null,
            null,
            null,
            emptyContentStream(),
            emptyContentStream(),
            emptyContentStream(),
            objectStream(packed),
        )

        return assemble(objects, extraTrailerEntries = "")
    }

    /**
     * A book whose encryption has been taken off: an older revision whose
     * trailer declares an `/Encrypt`, and an appended one that does not.
     *
     * How every shop that sells DRM-free copies of an encrypted book produces
     * one. The last trailer is the current one, and a reader that asks whether
     * *any* trailer names an /Encrypt refuses to read a book it can read
     * perfectly well.
     */
    fun withEncryptionRemoved(): ByteArray =
        assemble(
            listOf(
                "<< /Type /Catalog /Pages 2 0 R /Outlines 6 0 R >>",
                "<< /Type /Pages /Kids [3 0 R 4 0 R 5 0 R] /Count 3 >>",
                page(contentsObject = 9),
                page(contentsObject = 10),
                page(contentsObject = 11),
                "<< /Type /Outlines /First 7 0 R /Last 8 0 R /Count 2 >>",
                "<< /Title ($FIRST_CHAPTER_TITLE) /Parent 6 0 R /Next 8 0 R /Dest [3 0 R /Fit] >>",
                "<< /Title ($SECOND_CHAPTER_TITLE) /Parent 6 0 R /Prev 7 0 R /Dest [5 0 R /Fit] >>",
                emptyContentStream(),
                emptyContentStream(),
                emptyContentStream(),
                "<< /Filter /Standard /V 2 /R 3 >>",
            ),
            extraTrailerEntries = " /Encrypt 12 0 R",
            appendedRevision = true,
        )

    /** The same book with the encryption still declared by its newest trailer. */
    fun stillEncrypted(): ByteArray =
        assemble(
            listOf(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                page(contentsObject = 4),
                emptyContentStream(),
                "<< /Title (A Title Nobody Can Read) >>",
                "<< /Filter /Standard /V 2 /R 3 >>",
            ),
            extraTrailerEntries = " /Info 5 0 R /Encrypt 6 0 R",
        )

    /**
     * A one-page document with [lines] actually drawn on it, each at the height
     * it is paired with.
     *
     * Every other fixture here has empty content streams, which is enough to
     * find a chapter list and nothing at all to read. This one draws real text
     * in Helvetica - a base-14 font, so nothing has to be embedded - which is
     * what lets the platform that reads a page's words be asked to read some.
     *
     * The same bytes on the desktop, in the simulator and on a device, which is
     * what makes the three sets of results comparable. `PdfPageTextTest` on the
     * JVM reads this file too, so a fixture that stopped being readable would
     * fail somewhere it can be run.
     */
    fun withText(vararg lines: Pair<String, Int>): ByteArray {
        val stream = drawn(lines.toList())

        return assemble(
            listOf(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] " +
                    "/Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>",
                stream,
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
            ),
            extraTrailerEntries = "",
        )
    }

    /**
     * The content stream that draws [lines].
     *
     * PDF measures upwards from the bottom of the page, so a line paired with a
     * larger number is nearer the top - which is the conversion a reader has to
     * undo, and the reason a test hands these in as page coordinates rather than
     * as an order.
     */
    private fun drawn(lines: List<Pair<String, Int>>): String {
        val contents = buildString {
            append("BT\n/F1 12 Tf\n")
            lines.forEach { (text, fromBottom) ->
                append("1 0 0 1 72 ").append(fromBottom).append(" Tm\n")
                append('(').append(text.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)"))
                append(") Tj\n")
            }
            append("ET")
        }

        return "<< /Length ${contents.length} >>\nstream\n$contents\nendstream"
    }

    /**
     * `<< /Type /ObjStm ... >>` holding [packed], laid out the way the format
     * asks: a header of `objectNumber offset` pairs, then the objects, with
     * every offset counted from `/First`.
     */
    private fun objectStream(packed: List<Pair<Int, String>>): String {
        val header = StringBuilder()
        val body = StringBuilder()

        packed.forEach { (number, content) ->
            header.append(number).append(' ').append(body.length).append(' ')
            body.append(content).append(' ')
        }

        val contents = header.toString() + body.toString()

        return "<< /Type /ObjStm /N ${packed.size} /First ${header.length} " +
            "/Length ${contents.length} >>\nstream\n$contents\nendstream"
    }

    private fun assemble(
        objects: List<String?>,
        extraTrailerEntries: String,
        appendedRevision: Boolean = false,
    ): ByteArray {
        val document = StringBuilder("%PDF-1.4\n")
        val offsets = mutableListOf<Int?>()

        objects.forEachIndexed { index, body ->
            if (body == null) {
                offsets += null
                return@forEachIndexed
            }

            offsets += document.length
            document.append(index + 1).append(" 0 obj\n").append(body).append("\nendobj\n")
        }

        val crossReferenceOffset = document.length

        // Every entry is exactly twenty bytes, which is what lets a reader index
        // straight into the table.
        document.append("xref\n0 ").append(objects.size + 1).append('\n')
        document.append("0000000000 65535 f \n")
        offsets.forEach { offset ->
            when (offset) {
                null -> document.append("0000000000 65535 f \n")
                else -> document.append(offset.toString().padStart(10, '0')).append(" 00000 n \n")
            }
        }

        document.append("trailer\n<< /Size ").append(objects.size + 1)
            .append(" /Root 1 0 R").append(extraTrailerEntries)
            .append(" >>\nstartxref\n")
            .append(crossReferenceOffset)
            .append("\n%%EOF\n")

        // A revision that changes nothing but the trailer, which is what a file
        // whose encryption was removed carries: the same catalog, said again by
        // a trailer that no longer declares an /Encrypt.
        if (appendedRevision) {
            document.append("trailer\n<< /Size ").append(objects.size + 1)
                .append(" /Root 1 0 R /Prev ").append(crossReferenceOffset)
                .append(" >>\nstartxref\n")
                .append(crossReferenceOffset)
                .append("\n%%EOF\n")
        }

        // Every byte written here is ASCII, so string length is byte length and
        // the offsets above are the ones a reader will find.
        return document.toString().encodeToByteArray()
    }

    private fun page(contentsObject: Int): String =
        "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] " +
            "/Resources << >> /Contents $contentsObject 0 R >>"

    private fun emptyContentStream(): String = "<< /Length 0 >>\nstream\n\nendstream"
}
