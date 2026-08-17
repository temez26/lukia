package io.lukia.core.domain

/**
 * What a document says about itself.
 *
 * Read out of the file at import time and stored beside the library entry, so
 * the detail screen can show a book's publisher without opening the book. Every
 * field is optional because every field is optional in every format that carries
 * them - a great many EPUBs name only a title and an author, and a PDF written
 * by a word processor often names nothing worth showing at all.
 *
 * Deliberately not a merge of "what the file says" and "what the app knows". The
 * file size, the format and where the file lives are facts about the file rather
 * than claims the document makes about itself, and they live on [DocumentInfo].
 */
data class BookMetadata(
    /**
     * The title the document declares, which is not the file name. Null when the
     * document declares none, in which case the file name remains the honest
     * answer.
     */
    val title: String? = null,
    /**
     * Everyone credited with writing it. A list rather than a string because
     * books have more than one author and joining them here would mean every
     * reader of this having to take them apart again.
     */
    val authors: List<String> = emptyList(),
    val publisher: String? = null,
    /**
     * When it was published, as the document wrote it. Kept as text rather than
     * parsed to a date: the formats allow a full timestamp, a bare year, and a
     * good deal in between, and a year shown as a year is what a reader wants
     * from all of them.
     */
    val published: String? = null,
    /** A BCP 47 tag as written - `en`, `en-GB`, `fi`. Presented as a language name. */
    val language: String? = null,
    /** The publisher's blurb. Plain text: any markup the source carried is stripped. */
    val description: String? = null,
    /** Genres, keywords, BISAC headings - whatever the document filed itself under. */
    val subjects: List<String> = emptyList(),
    /** An ISBN where the document carries one, otherwise whatever identifier it does. */
    val identifier: String? = null
) {
    /**
     * True when the document told us nothing. Worth asking before showing a
     * section that would otherwise be a heading over an empty space.
     */
    val isEmpty: Boolean
        get() = title == null &&
            authors.isEmpty() &&
            publisher == null &&
            published == null &&
            language == null &&
            description == null &&
            subjects.isEmpty() &&
            identifier == null

    companion object {
        val Empty = BookMetadata()
    }
}
