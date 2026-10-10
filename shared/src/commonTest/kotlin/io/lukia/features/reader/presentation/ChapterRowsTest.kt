package io.lukia.features.reader.presentation

import io.lukia.features.reader.domain.outline.OutlineEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChapterRowsTest {

    private val outline = listOf(
        entry("Introduction", page = 0, level = 0),
        entry("The Middle", page = 119, level = 0),
        entry("A Section", page = 129, level = 1),
        entry("A Sub-section", page = 133, level = 2),
        entry("Another Section", page = 140, level = 1),
        entry("The End", page = 300, level = 0),
    )

    /** A chapter is a chapter because of what follows it, not because of itself. */
    @Test
    fun anEntryWithDeeperEntriesUnderItHasChildren() {
        val rows = chapterRows(outline, collapsed = emptySet())

        assertFalse(rows.first { it.entry.title == "Introduction" }.hasChildren)
        assertTrue(rows.first { it.entry.title == "The Middle" }.hasChildren)
        assertTrue(rows.first { it.entry.title == "A Section" }.hasChildren)
        assertFalse(rows.first { it.entry.title == "Another Section" }.hasChildren)
    }

    @Test
    fun nothingIsHiddenWhenNothingIsCollapsed() {
        val rows = chapterRows(outline, collapsed = emptySet())

        assertEquals(outline.map { it.title }, rows.map { it.entry.title })
    }

    /** Folding a chapter away takes its sections' own sub-sections with it. */
    @Test
    fun collapsingAChapterHidesEverythingUnderIt() {
        val rows = chapterRows(outline, collapsed = setOf(1))

        assertEquals(
            listOf("Introduction", "The Middle", "The End"),
            rows.map { it.entry.title },
        )
        assertTrue(rows.first { it.entry.title == "The Middle" }.isCollapsed)
    }

    /** And stops at the next entry at its own level, rather than at the end. */
    @Test
    fun collapsingASectionHidesOnlyItsOwnSubSections() {
        val rows = chapterRows(outline, collapsed = setOf(2))

        assertEquals(
            listOf("Introduction", "The Middle", "A Section", "Another Section", "The End"),
            rows.map { it.entry.title },
        )
    }

    /** A row with nothing under it has nothing to fold, whatever the set says. */
    @Test
    fun collapsingALeafChangesNothing() {
        val rows = chapterRows(outline, collapsed = setOf(0))

        assertEquals(outline.size, rows.size)
        assertFalse(rows.first().isCollapsed)
    }

    @Test
    fun onlyTheEntriesWithSectionsUnderThemCanBeCollapsed() {
        assertEquals(setOf(1, 2), collapsibleIndices(outline))
    }

    @Test
    fun anOutlineWithNoNestingHasNothingToCollapse() {
        val flat = listOf(entry("One", page = 0, level = 0), entry("Two", page = 5, level = 0))

        assertEquals(emptySet(), collapsibleIndices(flat))
    }

    @Test
    fun theCurrentEntryIsTheLastOneStartingAtOrBeforeThePage() {
        assertEquals(0, currentEntryIndex(outline, pageIndex = 0))
        assertEquals(0, currentEntryIndex(outline, pageIndex = 118))
        assertEquals(1, currentEntryIndex(outline, pageIndex = 119))
        assertEquals(3, currentEntryIndex(outline, pageIndex = 135))
        assertEquals(5, currentEntryIndex(outline, pageIndex = 400))
    }

    /**
     * A file's contents are not always in page order, and an entry at the end
     * of the list pointing back at the front is not where every page is.
     */
    @Test
    fun anEntryOutOfPageOrderDoesNotClaimThePagesAfterIt() {
        val trailing = outline + entry("Index", page = 0, level = 0)

        assertEquals(3, currentEntryIndex(trailing, pageIndex = 135))
        assertEquals(trailing.lastIndex, currentEntryIndex(trailing, pageIndex = 0))
    }

    /** A cover or a title page sits before the first entry, and belongs to none. */
    @Test
    fun aPageBeforeTheFirstEntryIsInsideNothing() {
        val late = listOf(entry("Chapter One", page = 4, level = 0))

        assertEquals(-1, currentEntryIndex(late, pageIndex = 0))
    }

    private fun entry(title: String, page: Int, level: Int) =
        OutlineEntry(title = title, pageIndex = page, level = level)
}
