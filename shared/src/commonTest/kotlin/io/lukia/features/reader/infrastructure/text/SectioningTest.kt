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

    /** A section's title is set as a chapter opening; the headings after it are not. */
    @Test
    fun theHeadingASectionOpensWithOpensAChapter() {
        val sections = sectionText(
            listOf(
                TextBlock.Heading(listOf(TextRun("One")), level = 1),
                TextBlock.Paragraph(listOf(TextRun("Words."))),
                TextBlock.Heading(listOf(TextRun("A part")), level = 2),
                TextBlock.Heading(listOf(TextRun("Two")), level = 1),
            ),
        ).sections

        val openings = sections.flatMap { section ->
            section.blocks.filterIsInstance<TextBlock.Heading>().map { it.text.single().text to it.opensChapter }
        }

        assertEquals(listOf("One" to true, "A part" to false, "Two" to true), openings)
    }

    /** A picture may come before the title; prose may not. */
    @Test
    fun onlyNothingButPicturesMayComeBeforeAChapterOpening() {
        val afterPicture = listOf(
            TextBlock.Separator,
            TextBlock.Heading(listOf(TextRun("Title")), level = 1),
        ).withChapterOpening()

        val afterProse = listOf(
            TextBlock.Paragraph(listOf(TextRun("Words."))),
            TextBlock.Heading(listOf(TextRun("Title")), level = 1),
        ).withChapterOpening()

        assertTrue((afterPicture[1] as TextBlock.Heading).opensChapter)
        assertEquals(false, (afterProse[1] as TextBlock.Heading).opensChapter)
    }

    /** "CHAPTER 5:" over the chapter's name is its label, not a second title. */
    @Test
    fun aNumberedLineOverTheTitleIsItsLabel() {
        val roles = listOf(
            TextBlock.Heading(listOf(TextRun("CHAPTER 5:")), level = 2),
            TextBlock.Heading(listOf(TextRun("User's Input and Loop")), level = 2),
            TextBlock.Paragraph(listOf(TextRun("Words."))),
        ).withChapterOpening().filterIsInstance<TextBlock.Heading>().map { it.chapter }

        assertEquals(listOf(TextBlock.ChapterRole.Label, TextBlock.ChapterRole.TitleUnderLabel), roles)
    }

    /** A short title with a heading straight under it is still the title. */
    @Test
    fun aShortUnnumberedTitleIsNotALabel() {
        val roles = listOf(
            TextBlock.Heading(listOf(TextRun("Introduction")), level = 1),
            TextBlock.Heading(listOf(TextRun("Who is this for?")), level = 2),
            TextBlock.Paragraph(listOf(TextRun("Words."))),
        ).withChapterOpening().filterIsInstance<TextBlock.Heading>().map { it.chapter }

        assertEquals(listOf(TextBlock.ChapterRole.Title, TextBlock.ChapterRole.None), roles)
    }
}
