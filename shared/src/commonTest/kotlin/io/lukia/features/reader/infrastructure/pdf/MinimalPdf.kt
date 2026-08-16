package io.lukia.features.reader.infrastructure.pdf

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
internal object MinimalPdf {

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
            emptyContentStream()
        )

        val document = StringBuilder("%PDF-1.4\n")
        val offsets = mutableListOf<Int>()

        objects.forEachIndexed { index, body ->
            offsets += document.length
            document.append(index + 1).append(" 0 obj\n").append(body).append("\nendobj\n")
        }

        val crossReferenceOffset = document.length

        // Every entry is exactly twenty bytes, which is what lets a reader index
        // straight into the table.
        document.append("xref\n0 ").append(objects.size + 1).append('\n')
        document.append("0000000000 65535 f \n")
        offsets.forEach { offset ->
            document.append(offset.toString().padStart(10, '0')).append(" 00000 n \n")
        }

        document.append("trailer\n<< /Size ").append(objects.size + 1)
            .append(" /Root 1 0 R >>\nstartxref\n")
            .append(crossReferenceOffset)
            .append("\n%%EOF\n")

        // Every byte written here is ASCII, so string length is byte length and
        // the offsets above are the ones a reader will find.
        return document.toString().encodeToByteArray()
    }

    private fun page(contentsObject: Int): String =
        "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] " +
            "/Resources << >> /Contents $contentsObject 0 R >>"

    private fun emptyContentStream(): String = "<< /Length 0 >>\nstream\n\nendstream"
}
