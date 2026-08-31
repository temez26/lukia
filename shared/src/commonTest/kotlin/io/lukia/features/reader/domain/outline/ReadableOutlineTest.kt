package io.lukia.features.reader.domain.outline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReadableOutlineTest {

    /**
     * The names a word processor leaves in the file. All of these have turned up
     * in the outline of a real book, listed as if they were chapters.
     */
    @Test
    fun theProducingToolsOwnBookmarksAreNotChapters() {
        val outline = listOf(
            entry("__DdeLink__2128_1572349441"),
            entry("__RefHeading__12345_678901234"),
            entry("__UnoMark__42_1572349441"),
            entry("_Toc148392017"),
            entry("_Ref90210"),
            entry("_Hlk61423"),
            entry("_GoBack"),
            entry("OLE_LINK1"),
            entry("Chapter One"),
        )

        assertEquals(listOf("Chapter One"), outline.asTableOfContents().map { it.title })
    }

    /** A heading someone wrote is left alone, however it starts. */
    @Test
    fun realTitlesSurvive() {
        val outline = listOf(
            entry("Introduction"),
            entry("_why's Poignant Guide"),
            entry("Reference: TOC handling"),
            entry("Table of Contents"),
            entry("Refactoring"),
            entry("Interfaces"),
        )

        assertEquals(outline.map { it.title }, outline.asTableOfContents().map { it.title })
    }

    /** A section whose chapter was dropped is a chapter now, not a stray indent. */
    @Test
    fun sectionsLeftBehindByADroppedEntryMoveUp() {
        val outline = listOf(
            entry("_Toc001", level = 0),
            entry("The Real Chapter", level = 1),
            entry("A Section", level = 2),
        )

        assertEquals(
            listOf("The Real Chapter" to 0, "A Section" to 1),
            outline.asTableOfContents().map { it.title to it.level },
        )
    }

    /** Producers skip levels on their own, without any help from the filter. */
    @Test
    fun aLevelTheFileSkippedIsClosedUp() {
        val outline = listOf(
            entry("Chapter", level = 0),
            entry("Section", level = 3),
            entry("Sub-section", level = 5),
            entry("Next Chapter", level = 0),
        )

        assertEquals(
            listOf(0, 1, 2, 0),
            outline.asTableOfContents().map { it.level },
        )
    }

    /** Nothing to fix is nothing changed - the same entries, at the same depths. */
    @Test
    fun aWellFormedOutlineIsUntouched() {
        val outline = listOf(
            entry("Chapter", level = 0),
            entry("Section", level = 1),
            entry("Next Chapter", level = 0),
        )

        assertEquals(outline, outline.asTableOfContents())
    }

    @Test
    fun anEmptyOutlineStaysEmpty() {
        assertEquals(emptyList(), emptyList<OutlineEntry>().asTableOfContents())
    }

    private fun entry(title: String, level: Int = 0) =
        OutlineEntry(title = title, pageIndex = 0, level = level)
}

/**
 * Which chapter a page falls under - asked when a sentence is tapped on a PDF,
 * whose pages carry no chapter of their own the way a reflowable section does.
 */
class ChapterCoveringTest {

    private val contents = listOf(
        OutlineEntry(title = "Introduction", pageIndex = 4, level = 0),
        OutlineEntry(title = "Functions", pageIndex = 180, level = 0),
        OutlineEntry(title = "Closures", pageIndex = 194, level = 1),
        OutlineEntry(title = "Classes", pageIndex = 230, level = 0),
    )

    @Test
    fun aPageBelongsToTheHeadingItComesAfter() {
        assertEquals("Functions", contents.chapterCovering(185))
        assertEquals("Classes", contents.chapterCovering(400))
    }

    /** The page a chapter starts on is inside that chapter. */
    @Test
    fun aChaptersFirstPageIsItsOwn() {
        assertEquals("Functions", contents.chapterCovering(180))
    }

    /**
     * The nearer, more specific heading wins. "Closures" settles more about a
     * paragraph than "Functions" does.
     */
    @Test
    fun aSectionInsideAChapterIsTheBetterAnswer() {
        assertEquals("Closures", contents.chapterCovering(200))
    }

    /**
     * A title page belongs to no chapter, and claiming it belongs to the first
     * one would be inventing something the book never said.
     */
    @Test
    fun aPageBeforeTheFirstHeadingBelongsToNothing() {
        assertNull(contents.chapterCovering(0))
        assertNull(contents.chapterCovering(3))
    }

    @Test
    fun aBookWithNoContentsAnswersNothing() {
        assertNull(emptyList<OutlineEntry>().chapterCovering(10))
    }

    @Test
    fun aHeadingWithNoTitleIsNotATitle() {
        val nameless = listOf(OutlineEntry(title = "   ", pageIndex = 0, level = 0))

        assertNull(nameless.chapterCovering(5))
    }
}
