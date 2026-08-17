package io.lukia.features.translate.domain

import io.lukia.core.domain.BookMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TranslationContextTest {

    @Test
    fun theDocumentsOwnTitleWinsOverWhatTheReaderCallsIt() {
        val context = BookMetadata(title = "Moby-Dick; or, The Whale")
            .asTranslationContext(bookTitle = "moby dick (1)")

        assertEquals("Moby-Dick; or, The Whale", context.bookTitle)
    }

    /**
     * A great many documents declare no title. The name the reader sees is then
     * the honest answer, and is still worth sending.
     */
    @Test
    fun theReadersNameForTheBookIsUsedWhenTheDocumentDeclaresNone() {
        val context = BookMetadata(authors = listOf("Anon"))
            .asTranslationContext(bookTitle = "notes on whaling")

        assertEquals("notes on whaling", context.bookTitle)
    }

    @Test
    fun aDocumentWithNoMetadataAtAllStillCarriesWhatTheReaderKnows() {
        val context = null.asTranslationContext(
            bookTitle = "notes",
            chapterTitle = "Two",
            surroundingText = "Some words."
        )

        assertEquals("notes", context.bookTitle)
        assertEquals("Two", context.chapterTitle)
        assertEquals("Some words.", context.surroundingText)
        assertTrue(context.authors.isEmpty())
        assertTrue(context.genres.isEmpty())
    }

    @Test
    fun subjectsBecomeTheGenresThatDisambiguateTheSentence() {
        val context = BookMetadata(subjects = listOf("Adventure fiction", "Whaling"))
            .asTranslationContext()

        assertEquals(listOf("Adventure fiction", "Whaling"), context.genres)
    }

    /**
     * An EPUB can file itself under thirty BISAC headings, and the thirtieth
     * says less about the book than the first three do - while costing latency
     * on every tap.
     */
    @Test
    fun longListsAreTrimmedToWhatActuallyHelps() {
        val context = BookMetadata(
            authors = List(9) { "Author $it" },
            subjects = List(30) { "Subject $it" }
        ).asTranslationContext()

        assertEquals(3, context.authors.size)
        assertEquals(6, context.genres.size)
        assertEquals("Author 0", context.authors.first())
    }

    /** Blank is not a value: a field nobody filled in should be left out. */
    @Test
    fun blankFieldsAreDroppedRatherThanSentEmpty() {
        val context = BookMetadata(
            title = "   ",
            authors = listOf("", "Melville", "  "),
            language = "",
            subjects = listOf("  ")
        ).asTranslationContext(bookTitle = "  ", chapterTitle = "", surroundingText = " ")

        assertNull(context.bookTitle)
        assertNull(context.sourceLanguage)
        assertNull(context.chapterTitle)
        assertNull(context.surroundingText)
        assertEquals(listOf("Melville"), context.authors)
        assertTrue(context.genres.isEmpty())
    }

    @Test
    fun aContextThatSaysNothingKnowsItSaysNothing() {
        assertTrue(TranslationContext.None.isEmpty)
        assertTrue(null.asTranslationContext().isEmpty)
        assertTrue(BookMetadata.Empty.asTranslationContext().isEmpty)
        assertFalse(BookMetadata(title = "Moby-Dick").asTranslationContext().isEmpty)
    }
}
