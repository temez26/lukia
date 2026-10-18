package io.lukia.features.reader.infrastructure.text

import io.lukia.features.reader.domain.outline.OutlineEntry
import io.lukia.features.reader.domain.text.TextBlock
import io.lukia.features.reader.domain.text.TextRun
import io.lukia.features.reader.domain.text.TextSection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Filling in a chapter's sub-sections from its own headings, for the many books
 * that declare a contents of chapters and nothing else.
 */
class SectionHeadingsTest {

    private fun heading(text: String, level: Int): TextBlock =
        TextBlock.Heading(listOf(TextRun(text)), level = level)

    private fun paragraph(text: String): TextBlock =
        TextBlock.Paragraph(listOf(TextRun(text)))

    /**
     * Closed with a paragraph, as a chapter is: a section of nothing but
     * headings is a title page, which offers no sections of its own.
     */
    private fun section(title: String?, vararg blocks: TextBlock) =
        TextSection(title = title, blocks = blocks.toList() + paragraph("Words."))

    private fun chapterEntry(title: String, section: Int = 0, level: Int = 0) =
        OutlineEntry(title = title, pageIndex = section, level = level, blockIndex = 0)

    private fun List<OutlineEntry>.shape() = map { Triple(it.title, it.level, it.blockIndex) }

    @Test
    fun aChapterTheBookSaidNothingAboutGetsItsHeadings() {
        val sections = listOf(
            section(
                "Chapter",
                heading("Chapter", 1),
                paragraph("Opening."),
                heading("First part", 2),
                paragraph("Words."),
                heading("Second part", 2),
            ),
        )

        val filled = listOf(chapterEntry("Chapter")).withSectionHeadings(sections)

        assertEquals(
            listOf(
                Triple("Chapter", 0, 0),
                Triple("First part", 1, 2),
                Triple("Second part", 1, 4),
            ),
            filled.shape(),
        )
    }

    /**
     * The book's own account of itself is better than one read off its headings,
     * and adding to it would list every section twice.
     */
    @Test
    fun aChapterTheBookAlreadyDescribedIsLeftAsItWasWritten() {
        val sections = listOf(
            section(
                "Chapter",
                heading("Chapter", 1),
                heading("First part", 2),
                heading("Second part", 2),
            ),
        )

        val contents = listOf(
            chapterEntry("Chapter"),
            OutlineEntry(title = "First part", pageIndex = 0, level = 1, blockIndex = 1),
        )

        assertEquals(contents, contents.withSectionHeadings(sections))
    }

    /** A chapter opening with its own name must not be nested underneath itself. */
    @Test
    fun theChaptersOwnTitleIsNotOfferedAsASectionOfIt() {
        val sections = listOf(
            section(
                "Chapter One",
                paragraph("A run-in line."),
                heading("Chapter One", 1),
                heading("Only part", 2),
            ),
        )

        val filled = listOf(chapterEntry("Chapter One")).withSectionHeadings(sections)

        assertEquals(listOf(Triple("Chapter One", 0, 0), Triple("Only part", 1, 2)), filled.shape())
    }

    /**
     * A chapter divided into `h2` and one divided into `h3` are both books
     * divided one level down; reading the number off the tag would indent one of
     * them for no reason a reader could see.
     */
    @Test
    fun levelsFollowTheShallowestHeadingFoundRatherThanTheTag() {
        val deep = listOf(
            section("Chapter", heading("Chapter", 1), heading("A part", 4), heading("Deeper", 5)),
        )

        val filled = listOf(chapterEntry("Chapter")).withSectionHeadings(deep)

        assertEquals(listOf(0, 1, 2), filled.map { it.level })
    }

    /** Deeper than that is a numbering scheme rather than a way to get around. */
    @Test
    fun nothingDeeperThanTwoLevelsIsOffered() {
        val sections = listOf(
            section(
                "Chapter",
                heading("Chapter", 1),
                heading("Part", 2),
                heading("Sub-part", 3),
                heading("Sub-sub-part", 4),
            ),
        )

        val filled = listOf(chapterEntry("Chapter")).withSectionHeadings(sections)

        assertEquals(listOf("Chapter", "Part", "Sub-part"), filled.map { it.title })
    }

    /**
     * A long chapter is laid out as several sections, and the ones after the
     * first carry no entry - their headings belong under the same chapter.
     */
    @Test
    fun aChapterThatRunsOnIntoMoreSectionsKeepsItsHeadings() {
        val sections = listOf(
            section("Chapter", heading("Chapter", 1), heading("First part", 2)),
            section(null, heading("Second part", 2)),
            section("Next", heading("Next", 1)),
        )

        val contents = listOf(chapterEntry("Chapter"), chapterEntry("Next", section = 2))
        val filled = contents.withSectionHeadings(sections)

        assertEquals(
            listOf("Chapter", "First part", "Second part", "Next"),
            filled.map { it.title },
        )
        assertEquals(listOf(0, 0, 1, 2), filled.map { it.pageIndex })
    }

    /** A link into the middle of a file names its own heading, not a section. */
    @Test
    fun aHeadingAtTheEntrysOwnBlockIsNotOfferedTwice() {
        val sections = listOf(
            section("File", heading("Front", 1), heading("Chapter", 1), heading("A part", 2)),
        )

        val entry = OutlineEntry(title = "Chapter", pageIndex = 0, level = 0, blockIndex = 1)
        val filled = listOf(entry).withSectionHeadings(sections)

        assertEquals(listOf(Triple("Chapter", 0, 1), Triple("A part", 1, 2)), filled.shape())
    }

    @Test
    fun aChapterWithNoHeadingsInItIsUnchanged() {
        val sections = listOf(section("Chapter", paragraph("Just words.")))
        val contents = listOf(chapterEntry("Chapter"))

        assertEquals(contents, contents.withSectionHeadings(sections))
    }

    @Test
    fun aBookWithNoContentsAtAllStaysThatWay() {
        assertTrue(emptyList<OutlineEntry>().withSectionHeadings(listOf(section("A"))).isEmpty())
    }

    /**
     * A heading every chapter has is the book's template, not a way in: a row of
     * "Challenges" under each of forty topics buries the topics.
     */
    @Test
    fun aHeadingRepeatedInEveryChapterIsLeftOut() {
        val sections = (1..3).map { number ->
            section(
                "Topic $number",
                heading("Topic $number", 1),
                heading("Part of topic $number", 2),
                heading("Challenges", 2),
            )
        }
        val entries = (0..2).map { chapterEntry("Topic ${it + 1}", section = it) }

        val filled = entries.withSectionHeadings(sections)

        assertEquals(
            listOf("Topic 1", "Part of topic 1", "Topic 2", "Part of topic 2", "Topic 3", "Part of topic 3"),
            filled.map { it.title },
        )
    }

    /** A title page set line by line - author, editor, publisher - is not a contents. */
    @Test
    fun aPageOfNothingButHeadingsOffersNoSections() {
        val titlePage = TextSection(title = null, blocks = listOf(heading("Title", 1), heading("Edited by", 2)))

        assertEquals(
            listOf("Title Page"),
            listOf(chapterEntry("Title Page")).withSectionHeadings(listOf(titlePage)).map { it.title },
        )
    }
}
