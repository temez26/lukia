package io.lukia.core.infrastructure.epub

import io.lukia.core.infrastructure.archive.storedZip
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * An EPUB opened far enough to find things in it, on every platform.
 *
 * This is common code that both the reader and the library's cover extraction
 * stand on, and until [MinimalEpub] existed the only thing that ever handed it
 * an archive was `jvmTest`. So a book was only ever opened on the desktop, and
 * the phone the app is mostly read on ran the same code against nothing.
 */
class EpubPackageTest {

    private fun opened(bytes: ByteArray) = assertNotNull(EpubPackage.open(bytes), "the book did not open")

    @Test
    fun opensABookThroughItsContainerAndPackageDocument() {
        val epub = opened(MinimalEpub.book())

        assertEquals(4, epub.manifest.size)
        assertEquals(listOf("one", "two"), epub.spine)
    }

    /** The spine is the order somebody reads in, resolved to actual entries. */
    @Test
    fun theReadingOrderIsTheSpineResolvedToEntries() {
        assertEquals(
            listOf("OEBPS/text/one.xhtml", "OEBPS/text/two.xhtml"),
            opened(MinimalEpub.book()).readingOrder.map { it.path },
        )
    }

    /**
     * Every href in the package document is written relative to it, so a book
     * whose package sits in a sub-directory - which is nearly all of them -
     * addresses nothing at all until they are resolved against it.
     */
    @Test
    fun everyHrefIsResolvedAgainstThePackageDocument() {
        val epub = opened(MinimalEpub.book())

        assertEquals(
            setOf(
                "OEBPS/images/cover.png",
                "OEBPS/nav.xhtml",
                "OEBPS/text/one.xhtml",
                "OEBPS/text/two.xhtml",
            ),
            epub.manifest.map { it.path }.toSet(),
        )
    }

    @Test
    fun readsAnEntryOutOfTheArchive() {
        val chapter = assertNotNull(opened(MinimalEpub.book()).read("OEBPS/text/one.xhtml"))

        assertEquals(true, chapter.decodeToString().contains(MinimalEpub.FIRST_CHAPTER_TITLE))
    }

    @Test
    fun anEntryTheBookDoesNotHaveIsNothing() {
        assertNull(opened(MinimalEpub.book()).read("OEBPS/text/three.xhtml"))
    }

    @Test
    fun readsWhatTheBookSaysAboutItself() {
        val metadata = opened(MinimalEpub.book()).metadata()

        assertEquals(MinimalEpub.TITLE, metadata.title)
        assertEquals(listOf(MinimalEpub.AUTHOR), metadata.authors)
    }

    /** EPUB 3 declares its cover as a manifest property. */
    @Test
    fun findsACoverDeclaredAsAProperty() {
        assertEquals(MinimalEpub.COVER_PATH, opened(MinimalEpub.book()).coverImagePath())
    }

    /**
     * EPUB 2 declares it as a `<meta name="cover">` naming a manifest id, and
     * most of a bought shelf is still EPUB 2.
     */
    @Test
    fun findsACoverDeclaredTheOlderWay() {
        assertEquals(MinimalEpub.COVER_PATH, opened(MinimalEpub.olderBook()).coverImagePath())
    }

    @Test
    fun theCoverBytesAreTheOnesTheBookCarries() {
        val epub = opened(MinimalEpub.book())
        val path = assertNotNull(epub.coverImagePath())

        assertContentEquals(MinimalEpub.coverBytes, epub.read(path))
    }

    @Test
    fun findsTheNavigationDocument() {
        assertEquals("OEBPS/nav.xhtml", opened(MinimalEpub.book()).navigationPath)
    }

    /** An EPUB 2 book has no nav document, and saying it has one is worse than saying nothing. */
    @Test
    fun anOlderBookHasNoNavigationDocument() {
        assertNull(opened(MinimalEpub.olderBook()).navigationPath)
    }

    /** A book with no container names no package document, and cannot be opened. */
    @Test
    fun anArchiveWithNoContainerIsNotABook() {
        assertNull(EpubPackage.open(storedZip(mapOf("something.txt" to "words".encodeToByteArray()))))
    }

    /** Nor is a container pointing at a package document that is not in the archive. */
    @Test
    fun aContainerPointingAtNothingIsNotABook() {
        val broken = storedZip(
            mapOf(
                "META-INF/container.xml" to """
                    <?xml version="1.0"?>
                    <container>
                      <rootfiles>
                        <rootfile full-path="OEBPS/gone.opf" media-type="application/oebps-package+xml"/>
                      </rootfiles>
                    </container>
                """.trimIndent().encodeToByteArray(),
            ),
        )

        assertNull(EpubPackage.open(broken))
    }

    @Test
    fun somethingThatIsNotAnArchiveIsNotABook() {
        assertNull(EpubPackage.open("not a book, just some words".encodeToByteArray()))
    }

    @Test
    fun aFileWithNothingInItIsNotABook() {
        assertNull(EpubPackage.open(ByteArray(0)))
    }
}
