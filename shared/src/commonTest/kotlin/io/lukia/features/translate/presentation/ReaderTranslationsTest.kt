package io.lukia.features.translate.presentation

import io.lukia.core.domain.AppError
import io.lukia.features.translate.domain.TextAnchor
import io.lukia.features.translate.domain.Translation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The filing has to put each translation where it belongs, and - the part that
 * actually earns this type - two slices with nothing different in them have to
 * compare equal. That equality is what tells Compose it can leave a page or a
 * paragraph exactly as it drew it last time, so the tests for it are tests about
 * whether translating a sentence is work for the rest of the book.
 */
class ReaderTranslationsTest {

    private val ready = PassageTranslation.Ready(Translation("Kutsukaa minua Ismaeliksi."))
    private val failed = PassageTranslation.Failed(AppError.TranslationFailed)

    @Test
    fun aPagesSentenceIsFiledUnderThatPage() {
        val translations = ReaderTranslations.of(mapOf(TextAnchor.Page(3, 7) to ready))

        assertEquals(ready, translations.page(3)[7])
    }

    @Test
    fun aSectionsSentenceIsFiledUnderThatSectionsBlock() {
        val translations = ReaderTranslations.of(mapOf(TextAnchor.Section(2, 5, 1) to ready))

        assertEquals(ready, translations.section(2).block(5)[1])
    }

    /** A page's sentence and a section's sentence are not the same sentence. */
    @Test
    fun pagesAndSectionsAreKeptApart() {
        val translations = ReaderTranslations.of(mapOf(TextAnchor.Page(0, 0) to ready))

        assertNull(translations.section(0).block(0)[0])
    }

    @Test
    fun somewhereNothingHasBeenTranslatedIsEmptyRatherThanMissing() {
        val translations = ReaderTranslations.of(mapOf(TextAnchor.Page(0, 0) to ready))

        assertTrue(translations.page(9).isEmpty)
        assertTrue(translations.section(9).block(0).isEmpty)
        assertFalse(translations.page(0).isEmpty)
    }

    @Test
    fun everythingIsFiledRatherThanTheFirstOfEach() {
        val translations = ReaderTranslations.of(
            mapOf(
                TextAnchor.Page(0, 0) to ready,
                TextAnchor.Page(0, 1) to failed,
                TextAnchor.Page(1, 0) to PassageTranslation.Loading,
                TextAnchor.Section(0, 0, 0) to ready,
                TextAnchor.Section(0, 1, 0) to failed
            )
        )

        assertEquals(ready, translations.page(0)[0])
        assertEquals(failed, translations.page(0)[1])
        assertEquals(PassageTranslation.Loading, translations.page(1)[0])
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
        val before = ReaderTranslations.of(mapOf(TextAnchor.Page(1, 0) to ready))
        val after = ReaderTranslations.of(
            mapOf(
                TextAnchor.Page(1, 0) to ready,
                TextAnchor.Page(5, 2) to PassageTranslation.Loading
            )
        )

        assertEquals(before.page(1), after.page(1))
        assertNotEquals(before.page(5), after.page(5))
    }

    /** The same, one paragraph at a time, for a book the reader sets itself. */
    @Test
    fun translatingInOneBlockLeavesTheOtherBlocksSlicesUnchanged() {
        val before = ReaderTranslations.of(mapOf(TextAnchor.Section(0, 0, 0) to ready))
        val after = ReaderTranslations.of(
            mapOf(
                TextAnchor.Section(0, 0, 0) to ready,
                TextAnchor.Section(0, 4, 1) to PassageTranslation.Loading
            )
        )

        assertEquals(before.section(0).block(0), after.section(0).block(0))
        assertNotEquals(before.section(0).block(4), after.section(0).block(4))
    }

    /** And a whole untouched section is one comparison, not one per block. */
    @Test
    fun translatingInOneSectionLeavesTheOtherSectionsUnchanged() {
        val before = ReaderTranslations.of(mapOf(TextAnchor.Section(0, 0, 0) to ready))
        val after = ReaderTranslations.of(
            mapOf(
                TextAnchor.Section(0, 0, 0) to ready,
                TextAnchor.Section(3, 0, 0) to ready
            )
        )

        assertEquals(before.section(0), after.section(0))
        assertNotEquals(before.section(3), after.section(3))
    }

    /** A sentence going from waiting to translated is a change where it is. */
    @Test
    fun aSentenceChangingStateChangesItsOwnSlice() {
        val waiting = ReaderTranslations.of(mapOf(TextAnchor.Page(0, 0) to PassageTranslation.Loading))
        val arrived = ReaderTranslations.of(mapOf(TextAnchor.Page(0, 0) to ready))

        assertNotEquals(waiting.page(0), arrived.page(0))
    }

    @Test
    fun aBookNobodyHasTranslatedAnythingInHasNothingAnywhere() {
        val translations = ReaderTranslations.of(emptyMap())

        assertTrue(translations.page(0).isEmpty)
        assertTrue(translations.section(0).block(0).isEmpty)
        assertEquals(SentenceTranslations.None, translations.page(0))
    }
}
