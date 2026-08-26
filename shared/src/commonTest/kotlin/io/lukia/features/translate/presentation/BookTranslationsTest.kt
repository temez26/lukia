package io.lukia.features.translate.presentation

import io.lukia.core.domain.AppError
import io.lukia.features.translate.domain.TextAnchor
import io.lukia.features.translate.domain.Translation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The filing has to put each translation where it belongs, and - the part that
 * actually earns this type - two slices with nothing different in them have to
 * compare equal. That equality is what tells Compose it can leave a page or a
 * paragraph exactly as it drew it last time, so the tests for it are tests about
 * whether translating a sentence is work for the rest of the book.
 */
class BookTranslationsTest {

    private val ready = PassageTranslation.Ready(Translation("Kutsukaa minua Ismaeliksi."))
    private val failed = PassageTranslation.Failed(AppError.TranslationFailed)

    /**
     * A word and the sentence it sits in are two different passages, and one
     * must not be read as the other - a sentence with a translated word in it is
     * still an untranslated sentence.
     */
    @Test
    fun aTranslatedWordIsFiledApartFromItsSentence() {
        val translations = BookTranslations.of(
            mapOf(
                TextAnchor.Section(0, 0, 1, wordIndex = 2) to
                    PassageTranslation.Ready(Translation("valas")),
            ),
        )

        val block = translations.section(0).block(0)

        assertNull(block[1], "a translated word made its sentence look translated")
        assertEquals(
            PassageTranslation.Ready(Translation("valas")),
            block.words(1)[2],
        )
    }

    @Test
    fun aSentenceAndAWordInsideItAreBothKept() {
        val translations = BookTranslations.of(
            mapOf(
                TextAnchor.Page(3, 0) to PassageTranslation.Loading(),
                TextAnchor.Page(3, 0, wordIndex = 1) to
                    PassageTranslation.Ready(Translation("valas")),
            ),
        )

        val page = translations.page(3)

        assertEquals(PassageTranslation.Loading(), page[0])
        assertEquals(PassageTranslation.Ready(Translation("valas")), page.words(0)[1])
    }

    @Test
    fun aPagesSentenceIsFiledUnderThatPage() {
        val translations = BookTranslations.of(mapOf(TextAnchor.Page(3, 7) to ready))

        assertEquals(ready, translations.page(3)[7])
    }

    @Test
    fun aSectionsSentenceIsFiledUnderThatSectionsBlock() {
        val translations = BookTranslations.of(mapOf(TextAnchor.Section(2, 5, 1) to ready))

        assertEquals(ready, translations.section(2).block(5)[1])
    }

    /** A page's sentence and a section's sentence are not the same sentence. */
    @Test
    fun pagesAndSectionsAreKeptApart() {
        val translations = BookTranslations.of(mapOf(TextAnchor.Page(0, 0) to ready))

        assertNull(translations.section(0).block(0)[0])
    }

    @Test
    fun somewhereNothingHasBeenTranslatedIsEmptyRatherThanMissing() {
        val translations = BookTranslations.of(mapOf(TextAnchor.Page(0, 0) to ready))

        assertTrue(translations.page(9).isEmpty)
        assertTrue(translations.section(9).block(0).isEmpty)
        assertFalse(translations.page(0).isEmpty)
    }

    @Test
    fun everythingIsFiledRatherThanTheFirstOfEach() {
        val translations = BookTranslations.of(
            mapOf(
                TextAnchor.Page(0, 0) to ready,
                TextAnchor.Page(0, 1) to failed,
                TextAnchor.Page(1, 0) to PassageTranslation.Loading(),
                TextAnchor.Section(0, 0, 0) to ready,
                TextAnchor.Section(0, 1, 0) to failed,
            ),
        )

        assertEquals(ready, translations.page(0)[0])
        assertEquals(failed, translations.page(0)[1])
        assertEquals(PassageTranslation.Loading(), translations.page(1)[0])
        assertEquals(ready, translations.section(0).block(0)[0])
        assertEquals(failed, translations.section(0).block(1)[0])
    }

    /**
     * The one that matters: a sentence on one page changing state must leave
     * every other page's slice equal to what it was, or the whole book is drawn
     * again on every tap.
     */
    @Test
    fun translatingOnOnePageLeavesTheOtherPagesSlicesUnchanged() {
        val before = BookTranslations.of(mapOf(TextAnchor.Page(1, 0) to ready))
        val after = BookTranslations.of(
            mapOf(
                TextAnchor.Page(1, 0) to ready,
                TextAnchor.Page(5, 2) to PassageTranslation.Loading(),
            ),
        )

        assertEquals(before.page(1), after.page(1))
        assertNotEquals(before.page(5), after.page(5))
    }

    /** The same, one paragraph at a time, for a book the reader sets itself. */
    @Test
    fun translatingInOneBlockLeavesTheOtherBlocksSlicesUnchanged() {
        val before = BookTranslations.of(mapOf(TextAnchor.Section(0, 0, 0) to ready))
        val after = BookTranslations.of(
            mapOf(
                TextAnchor.Section(0, 0, 0) to ready,
                TextAnchor.Section(0, 4, 1) to PassageTranslation.Loading(),
            ),
        )

        assertEquals(before.section(0).block(0), after.section(0).block(0))
        assertNotEquals(before.section(0).block(4), after.section(0).block(4))
    }

    /** And a whole untouched section is one comparison, not one per block. */
    @Test
    fun translatingInOneSectionLeavesTheOtherSectionsUnchanged() {
        val before = BookTranslations.of(mapOf(TextAnchor.Section(0, 0, 0) to ready))
        val after = BookTranslations.of(
            mapOf(
                TextAnchor.Section(0, 0, 0) to ready,
                TextAnchor.Section(3, 0, 0) to ready,
            ),
        )

        assertEquals(before.section(0), after.section(0))
        assertNotEquals(before.section(3), after.section(3))
    }

    /** A sentence going from waiting to translated is a change where it is. */
    @Test
    fun aSentenceChangingStateChangesItsOwnSlice() {
        val waiting = BookTranslations.of(mapOf(TextAnchor.Page(0, 0) to PassageTranslation.Loading()))
        val arrived = BookTranslations.of(mapOf(TextAnchor.Page(0, 0) to ready))

        assertNotEquals(waiting.page(0), arrived.page(0))
    }

    @Test
    fun aBookNobodyHasTranslatedAnythingInHasNothingAnywhere() {
        val translations = BookTranslations.of(emptyMap())

        assertTrue(translations.page(0).isEmpty)
        assertTrue(translations.section(0).block(0).isEmpty)
        assertEquals(SentenceTranslations.None, translations.page(0))
    }

    /**
     * A paragraph too long for one page is drawn in pieces, and each piece
     * numbers its own sentences from zero - while the book counts them from the
     * start of the paragraph.
     */
    @Test
    fun aSliceOfABlockSeesItsOwnSentencesNumberedFromZero() {
        val translations = BookTranslations.of(
            mapOf(
                TextAnchor.Section(sectionIndex = 0, blockIndex = 0, sentenceIndex = 4) to ready,
            ),
        )

        val slice = translations.section(0).block(0).from(offset = 3)

        assertNotNull(slice[1])
        assertNull(slice[4])
    }

    /** Sentences left behind on an earlier page are not on this one to mark. */
    @Test
    fun sentencesBeforeTheSliceAreNotInIt() {
        val translations = BookTranslations.of(
            mapOf(
                TextAnchor.Section(sectionIndex = 0, blockIndex = 0, sentenceIndex = 1) to ready,
            ),
        )

        assertTrue(translations.section(0).block(0).from(offset = 3).isEmpty)
    }

    /** The first piece of a block is the block: nothing came before it. */
    @Test
    fun aSliceThatStartsAtTheBeginningIsTheBlockItself() {
        val translations = BookTranslations.of(
            mapOf(
                TextAnchor.Section(sectionIndex = 0, blockIndex = 0, sentenceIndex = 2) to ready,
            ),
        )

        val block = translations.section(0).block(0)

        assertEquals(block, block.from(offset = 0))
    }

    /** A paragraph translated whole stands in for all of it, on every page of it. */
    @Test
    fun aWholeBlockTranslationBelongsToEveryPieceOfIt() {
        val translations = BookTranslations.of(
            mapOf(
                TextAnchor.Section(sectionIndex = 0, blockIndex = 0, sentenceIndex = null) to ready,
            ),
        )

        assertEquals(ready, translations.section(0).block(0).from(offset = 5).block)
    }
}
