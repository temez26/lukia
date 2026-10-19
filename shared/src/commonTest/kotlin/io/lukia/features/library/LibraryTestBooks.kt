package io.lukia.features.library

import io.lukia.core.domain.document.BookMetadata
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.domain.document.DocumentType
import io.lukia.core.domain.document.PickedDocument
import io.lukia.core.domain.reading.ReadingProgress
import io.lukia.features.library.presentation.model.LibraryBook
import io.lukia.features.library.presentation.model.asLibrary

/**
 * Books for the tests around narrowing a library, built the way the screen
 * builds them - through [asLibrary], so the search and sort keys under test are
 * the ones the app actually derives rather than ones a test wrote by hand.
 */
internal class TestBook(
    val document: DocumentInfo,
    val progress: ReadingProgress?,
)

internal fun book(
    title: String,
    type: DocumentType = DocumentType.PDF,
    favorite: Boolean = false,
    authors: List<String> = emptyList(),
    subjects: List<String> = emptyList(),
    language: String? = null,
    declaredTitle: String? = null,
    sizeBytes: Long = 1024L,
    progress: ReadingProgress? = null,
    onServer: Boolean = false,
    exists: Boolean = true,
): TestBook {
    val extension = when (type) {
        DocumentType.EPUB -> "epub"
        DocumentType.DOCX -> "docx"
        DocumentType.TEXT -> "txt"
        DocumentType.MARKDOWN -> "md"
        else -> "pdf"
    }

    val metadata = BookMetadata(
        title = declaredTitle,
        authors = authors,
        language = language,
        subjects = subjects,
    ).takeIf { !it.isEmpty }

    return TestBook(
        document = DocumentInfo(
            document = PickedDocument(
                fileName = "$title.$extension",
                extension = extension,
                location = "/library/$title.$extension",
            ),
            type = type,
            exists = exists,
            sizeBytes = sizeBytes,
            isFavorite = favorite,
            metadata = metadata,
            onServer = onServer,
        ),
        progress = progress,
    )
}

internal fun library(vararg books: TestBook): List<LibraryBook> =
    books.map { it.document }.asLibrary(
        books.mapNotNull { book ->
            book.progress?.let { book.document.document.location to it }
        }.toMap(),
    )

/** What the cards would say, for assertions that read like the grid looks. */
internal fun List<LibraryBook>.titles(): List<String> =
    map { it.document.document.fileName.substringBeforeLast('.') }

/** A book someone is part way through, at a page that is neither the first nor the last. */
internal fun partRead(): ReadingProgress = ReadingProgress(pageIndex = 20, pageCount = 100)

/** A book someone has reached the last page of. */
internal fun finished(): ReadingProgress = ReadingProgress(pageIndex = 99, pageCount = 100)
