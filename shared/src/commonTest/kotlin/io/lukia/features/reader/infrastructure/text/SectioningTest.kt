package io.lukia.features.reader.infrastructure.text

import io.lukia.features.reader.domain.text.TextBlock
import io.lukia.features.reader.domain.text.TextRun
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SectioningTest {

    @Test
    fun theDocumentIsDividedAtItsTopLevelHeadings() {
        val sectioned = sectionText(
            listOf(
                heading("Chapter One", level = 1),
                paragraph("Words."),
                heading("Chapter Two", level = 1),
                paragraph("More words."),
            ),
        )

        assertEquals(listOf("Chapter One", "Chapter Two"), sectioned.sections.map { it.title })
        assertEquals(listOf(2, 2), sectioned.sections.map { it.blocks.size })
    }

    /**
     * Plenty of documents start at level two, or number their headings from
     * wherever a template left off. The top level is whatever the document uses.
     */
    @Test
    fun theTopLevelIsWhicheverTheDocumentActuallyUses() {
        val sectioned = sectionText(
            listOf(
                heading("Chapter One", level = 2),
                heading("A section", level = 3),
                heading("Chapter Two", level = 2),
            ),
        )

        assertEquals(listOf("Chapter One", "Chapter Two"), sectioned.sections.map { it.title })
        assertEquals(
            listOf("Chapter One" to 0, "A section" to 1, "Chapter Two" to 0),
            sectioned.outline.map { it.title to it.level },
        )
    }

    /** A sub-heading points at the section holding it, the smallest thing to scroll to. */
    @Test
    fun subHeadingsPointAtTheSectionTheyAreIn() {
        val sectioned = sectionText(
            listOf(
                heading("Chapter One", level = 1),
                heading("A section", level = 2),
                heading("Chapter Two", level = 1),
            ),
        )

        assertEquals(listOf(0, 0, 1), sectioned.outline.map { it.pageIndex })
    }

    /** Past two levels a document is listing its own numbering, not a way around it. */
    @Test
    fun theContentsGoTwoLevelsDeep() {
        val sectioned = sectionText(
            listOf(
                heading("Chapter", level = 1),
                heading("Section", level = 2),
                heading("Sub-section", level = 3),
            ),
        )

        assertEquals(listOf("Chapter", "Section"), sectioned.outline.map { it.title })
    }

    @Test
    fun whatComesBeforeTheFirstHeadingIsItsOwnUnnamedSection() {
        val sectioned = sectionText(
            listOf(
                paragraph("Front matter."),
                heading("Chapter One", level = 1),
                paragraph("Words."),
            ),
        )

        assertEquals(2, sectioned.sections.size)
        assertNull(sectioned.sections.first().title)
        assertEquals("Chapter One", sectioned.sections.last().title)
    }

    @Test
    fun aDocumentWithNoHeadingsHasNoContents() {
        val sectioned = sectionText(List(3) { paragraph("Words.") })

        assertEquals(1, sectioned.sections.size)
        assertEquals(emptyList(), sectioned.outline)
    }

    /** A chapter of ten thousand paragraphs is one list item that never settles. */
    @Test
    fun anEnormousSectionIsBrokenUpAndItsPiecesAreLeftUnnamed() {
        val blocks = listOf(heading("Chapter One", level = 1)) + List(1000) { paragraph("Words.") }

        val sectioned = sectionText(blocks)

        assertTrue(sectioned.sections.size > 1, "expected the chapter to be broken up")
        assertEquals("Chapter One", sectioned.sections.first().title)
        assertTrue(sectioned.sections.drop(1).all { it.title == null })
        assertEquals(blocks.size, sectioned.sections.sumOf { it.blocks.size })
    }

    private fun heading(title: String, level: Int) =
        TextBlock.Heading(text = listOf(TextRun(title)), level = level)

    private fun paragraph(text: String) = TextBlock.Paragraph(listOf(TextRun(text)))
}
