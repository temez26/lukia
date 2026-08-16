package io.lukia.core.domain


object TestDocuments {

    fun pdf(
        location: String
    ) = PickedDocument(
        fileName = "book.pdf",
        extension = "pdf",
        location = location
    )

    fun epub(
        location: String
    ) = PickedDocument(
        fileName = "book.epub",
        extension = "epub",
        location = location
    )

    fun uppercasePdf(
        location: String
    ) = PickedDocument(
        fileName = "book.PDF",
        extension = "PDF",
        location = location
    )

    fun text(
        location: String
    ) = PickedDocument(
        fileName = "book.txt",
        extension = "txt",
        location = location
    )

    /**
     * A format the library does not take. Kindle's own, chosen because it is a
     * real book format rather than an invented extension - the point of these
     * tests is that an unsupported document is turned away, and a file nobody
     * would ever try to import does not test that.
     */
    fun unsupported(
        location: String
    ) = PickedDocument(
        fileName = "book.mobi",
        extension = "mobi",
        location = location
    )

    fun missingPdf(
        location: String
    ) = PickedDocument(
        fileName = "missing.pdf",
        extension = "pdf",
        location = location
    )
}