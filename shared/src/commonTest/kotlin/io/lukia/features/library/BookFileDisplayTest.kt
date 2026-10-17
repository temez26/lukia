package io.lukia.features.library

import io.lukia.features.library.presentation.model.isbnOrNull
import io.lukia.features.library.presentation.model.readableLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** What the details screen says about a book's file and identifier. */
class BookFileDisplayTest {

    @Test
    fun anIsbnLosesItsUrnPrefix() {
        assertEquals("978-1-4919-5003-4", isbnOrNull("urn:isbn:978-1-4919-5003-4"))
        assertEquals("0306406152", isbnOrNull("ISBN 0306406152"))
        assertEquals("080442957X", isbnOrNull("080442957X"))
    }

    /** A UUID an EPUB tool made up is not an ISBN, and is not shown as one. */
    @Test
    fun anythingElseIsNotAnIsbn() {
        assertNull(isbnOrNull("urn:uuid:fabb3b03-5404-433a-abf2-1b63a1cb3820"))
        assertNull(isbnOrNull("calibre:1234"))
        assertNull(isbnOrNull("12345"))
    }

    @Test
    fun aDocumentPickerUriShowsItsPath() {
        val location = "content://com.android.externalstorage.documents/tree/primary%3ABooks" +
            "/document/primary%3ABooks%2FIT%2FProgramming%20Puzzles.epub"

        assertEquals("Books/IT/Programming Puzzles.epub", readableLocation(location))
    }

    @Test
    fun aPlainPathIsShownAsItIs() {
        assertEquals("/home/me/Books/Dune.epub", readableLocation("/home/me/Books/Dune.epub"))
    }

    @Test
    fun badEncodingIsShownRatherThanDropped() {
        assertEquals("/Books/100% Done.epub", readableLocation("/Books/100% Done.epub"))
    }
}
