package io.lukia.features.import

import io.lukia.features.import.domain.PickedDocument

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

    fun txt(
        location: String
    ) = PickedDocument(
        fileName = "book.txt",
        extension = "txt",
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