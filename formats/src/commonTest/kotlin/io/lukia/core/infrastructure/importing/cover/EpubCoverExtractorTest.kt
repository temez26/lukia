package io.lukia.core.infrastructure.importing.cover

import io.lukia.core.infrastructure.ByteArraySource
import io.lukia.core.infrastructure.epub.MinimalEpub
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

/**
 * The picture the library shows beside a book's title, found inside the book.
 *
 * The two ways a book declares one are the whole of this: EPUB 3 marks the
 * manifest item with a property, EPUB 2 points at it from a `<meta>`, and a
 * shelf of bought books has both on it.
 *
 * In `commonTest` against [MinimalEpub] rather than in `jvmTest` against a zip
 * the JDK wrote, so the same archive is opened on the desktop, on a device and
 * in the simulator - which is where books actually get imported.
 */
class EpubCoverExtractorTest {

    @Test
    fun findsACoverDeclaredTheWayEpubThreeDeclaresOne() {
        assertContentEquals(MinimalEpub.coverBytes, extractEpubCover(ByteArraySource(MinimalEpub.book())))
    }

    @Test
    fun findsACoverDeclaredTheWayEpubTwoDeclaresOne() {
        assertContentEquals(MinimalEpub.coverBytes, extractEpubCover(ByteArraySource(MinimalEpub.olderBook())))
    }

    @Test
    fun aBookThatDeclaresNoCoverHasNone() {
        assertNull(extractEpubCover(ByteArraySource(MinimalEpub.bookWithNoCover())))
    }

    @Test
    fun somethingThatIsNotAnEpubHasNoCover() {
        assertNull(extractEpubCover(ByteArraySource("not a book, just some words".encodeToByteArray())))
    }

    @Test
    fun aFileWithNothingInItHasNoCover() {
        assertNull(extractEpubCover(ByteArraySource(ByteArray(0))))
    }
}
