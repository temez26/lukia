package io.lukia.features.reader.domain

import kotlin.test.Test
import kotlin.test.assertEquals

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
            entry("Chapter One")
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
            entry("Interfaces")
        )

        assertEquals(outline.map { it.title }, outline.asTableOfContents().map { it.title })
    }

    /** A section whose chapter was dropped is a chapter now, not a stray indent. */
    @Test
    fun sectionsLeftBehindByADroppedEntryMoveUp() {
        val outline = listOf(
            entry("_Toc001", level = 0),
            entry("The Real Chapter", level = 1),
            entry("A Section", level = 2)
        )

        assertEquals(
            listOf("The Real Chapter" to 0, "A Section" to 1),
            outline.asTableOfContents().map { it.title to it.level }
        )
    }

    /** Producers skip levels on their own, without any help from the filter. */
    @Test
    fun aLevelTheFileSkippedIsClosedUp() {
        val outline = listOf(
            entry("Chapter", level = 0),
            entry("Section", level = 3),
            entry("Sub-section", level = 5),
            entry("Next Chapter", level = 0)
        )

        assertEquals(
            listOf(0, 1, 2, 0),
            outline.asTableOfContents().map { it.level }
        )
    }

    /** Nothing to fix is nothing changed - the same entries, at the same depths. */
    @Test
    fun aWellFormedOutlineIsUntouched() {
        val outline = listOf(
            entry("Chapter", level = 0),
            entry("Section", level = 1),
            entry("Next Chapter", level = 0)
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
