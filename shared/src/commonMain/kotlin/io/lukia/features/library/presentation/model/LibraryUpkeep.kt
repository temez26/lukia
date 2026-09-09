package io.lukia.features.library.presentation.model

/**
 * The two things that go wrong with a shelf nobody has tidied in a while:
 * books whose file is no longer where it was, and the same book added twice.
 *
 * Found rather than fixed. Both of these end by ticking the books on the shelf
 * and leaving the reader looking at them - see [LibrarySelection] - because
 * "these three, is that right?" is a question worth asking about somebody's
 * library, and an app that quietly tidied it would be answering it for them.
 */
data class LibraryUpkeep(
    /** Books the library lists whose file is gone or has moved. */
    val missing: Set<String> = emptySet(),
    /**
     * The later copies of a book that is on the shelf more than once - never the
     * first, so acting on every one of these leaves exactly one of each.
     */
    val duplicates: Set<String> = emptySet(),
) {
    companion object {
        val None = LibraryUpkeep()
    }
}

/**
 * What is worth tidying on this shelf.
 *
 * Duplicates agree on both title and size. Neither alone is enough - a library
 * holds several books called Poems, and many files share a byte count - but
 * together they are wrong rarely enough to be worth offering, and the reader
 * confirms every one.
 *
 * The shelf's existing order decides which copy is the original, so the same
 * shelf answers the same way twice.
 */
fun List<LibraryBook>.upkeep(): LibraryUpkeep {
    val seen = mutableSetOf<String>()
    val duplicates = mutableSetOf<String>()

    forEach { book ->
        if (!seen.add(book.copyKey())) duplicates += book.location
    }

    return LibraryUpkeep(
        missing = filterNot { it.document.exists }.map { it.location }.toSet(),
        duplicates = duplicates,
    )
}

/**
 * What makes two shelf entries the same book: what it is called, and how big it
 * is.
 *
 * The title the book declares where it has one, and the file name otherwise.
 * The same book downloaded twice is routinely `dracula.epub` and
 * `dracula (1).epub`, which agree on neither file name nor anything else the
 * shelf shows - but do agree on what the book says it is called.
 */
private fun LibraryBook.copyKey(): String {
    val declared = document.metadata?.title?.trim()?.lowercase()?.ifEmpty { null }

    return "${declared ?: sortTitle}\n${document.sizeBytes}"
}
