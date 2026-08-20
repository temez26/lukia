package io.lukia.features.reader.presentation.pagination

import io.lukia.features.reader.domain.ReaderOutline

/**
 * The book's contents, pointing at the pages the reader turns.
 *
 * A reflowable book's outline is read out of the file and so points at the
 * divisions the file states, and at a place inside one - which is what the pages
 * were made from, not the pages themselves. Moving the entries onto the layout
 * is what makes a chapter in the contents panel somewhere the reader can
 * actually be sent, and what lets the panel mark the chapter they are in.
 *
 * The place inside the division is what makes the sections work. A book names
 * its chapters by file and its sections by an anchor inside one, so a chapter of
 * forty sections is forty entries against one file; resolved by file alone they
 * would be forty rows that all go to the same page.
 *
 * A fixed-layout document is returned untouched: its outline already points at
 * its own pages, and there is no layout of the reader's own between the two.
 */
internal fun ReaderOutline.onPages(book: PaginatedBook, reflowable: Boolean): ReaderOutline {
    if (!reflowable || book.pages.isEmpty()) return this
    if (this !is ReaderOutline.Chapters) return this

    return ReaderOutline.Chapters(
        entries.map { entry ->
            entry.copy(pageIndex = book.pageOf(entry.pageIndex, entry.blockIndex))
        },
    )
}
