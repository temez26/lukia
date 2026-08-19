package io.lukia.features.translate.infrastructure

import io.lukia.features.translate.domain.TargetLanguage
import io.lukia.features.translate.domain.TranslationContext
import io.lukia.features.translate.domain.TranslationRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TranslationPromptTest {

    @Test
    fun theTargetLanguageIsNamedInWordsRatherThanAsATag() {
        val prompt = buildTranslationPrompt(request(targetLanguage = TargetLanguage.Finnish))

        assertTrue(prompt.system.contains("Finnish"), prompt.system)
        assertTrue(prompt.user.contains("Finnish"), prompt.user)
        assertFalse(prompt.system.contains("\"fi\""), prompt.system)
    }

    @Test
    fun thePassageIsTheLastThingSaid() {
        val prompt = buildTranslationPrompt(request(text = "Call me Ishmael."))

        assertTrue(prompt.user.trimEnd().endsWith("Call me Ishmael."), prompt.user)
    }

    /**
     * The whole point of the context: a model asked to translate four words in
     * isolation cannot know which sense of them the book meant.
     */
    @Test
    fun theBookAndItsGenreReachThePrompt() {
        val prompt = buildTranslationPrompt(
            request(
                context = TranslationContext(
                    bookTitle = "Moby-Dick",
                    authors = listOf("Herman Melville"),
                    genres = listOf("Adventure fiction", "Whaling"),
                    sourceLanguage = "en",
                    chapterTitle = "Loomings",
                ),
            ),
        )

        assertTrue(prompt.user.contains("Book: Moby-Dick"), prompt.user)
        assertTrue(prompt.user.contains("Author: Herman Melville"), prompt.user)
        assertTrue(prompt.user.contains("Genres: Adventure fiction, Whaling"), prompt.user)
        assertTrue(prompt.user.contains("Written in: en"), prompt.user)
        assertTrue(prompt.user.contains("Chapter: Loomings"), prompt.user)
    }

    @Test
    fun labelsAgreeWithHowManyThereAre() {
        val one = buildTranslationPrompt(
            request(context = TranslationContext(authors = listOf("Melville"), genres = listOf("Sea"))),
        )

        assertTrue(one.user.contains("Author: Melville"), one.user)
        assertTrue(one.user.contains("Genre: Sea"), one.user)

        val several = buildTranslationPrompt(
            request(context = TranslationContext(authors = listOf("A", "B"), genres = listOf("X", "Y"))),
        )

        assertTrue(several.user.contains("Authors: A, B"), several.user)
        assertTrue(several.user.contains("Genres: X, Y"), several.user)
    }

    /**
     * A plain text file declares nothing. Telling a model four times over that
     * it knows nothing is worse than telling it once, by leaving the line out.
     */
    @Test
    fun aBookThatSaysNothingAboutItselfSendsNoEmptyLabels() {
        val prompt = buildTranslationPrompt(request(context = TranslationContext.None))

        assertFalse(prompt.user.contains("Book:"), prompt.user)
        assertFalse(prompt.user.contains("Author"), prompt.user)
        assertFalse(prompt.user.contains("Genre"), prompt.user)
        assertFalse(prompt.user.contains("Unknown"), prompt.user)
        assertTrue(prompt.user.contains("Call me Ishmael."), prompt.user)
    }

    @Test
    fun theSurroundingTextIsOfferedAsContextRatherThanAsTheJob() {
        val prompt = buildTranslationPrompt(
            request(
                text = "Call me Ishmael.",
                context = TranslationContext(
                    surroundingText = "Call me Ishmael. Some years ago I went to sea.",
                ),
            ),
        )

        assertTrue(prompt.user.contains("The passage appears in this text:"), prompt.user)
        assertTrue(prompt.user.contains("Some years ago I went to sea."), prompt.user)
        assertTrue(prompt.user.contains("Translate this passage into"), prompt.user)
    }

    /** A paragraph of one sentence is its own surroundings, and says nothing twice. */
    @Test
    fun surroundingsIdenticalToThePassageAreLeftOut() {
        val prompt = buildTranslationPrompt(
            request(
                text = "Call me Ishmael.",
                context = TranslationContext(surroundingText = "Call me Ishmael."),
            ),
        )

        assertFalse(prompt.user.contains("The passage appears in"), prompt.user)
        assertEquals(1, prompt.user.split("Call me Ishmael.").size - 1, prompt.user)
    }

    /**
     * A sentence at the end of a long chapter would otherwise arrive with the
     * context of an entirely different part of the book.
     */
    @Test
    fun aLongContextIsWindowedAroundThePassageItself() {
        val passage = "The whale surfaced."
        val prompt = buildTranslationPrompt(
            request(
                text = passage,
                context = TranslationContext(
                    surroundingText = "filler. ".repeat(400) + passage + " tail words.",
                ),
            ),
        )

        assertTrue(prompt.user.contains(passage), prompt.user)
        assertTrue(prompt.user.contains("tail words."), prompt.user)
        assertTrue(prompt.user.length < 2500, "context was not trimmed: ${prompt.user.length}")
    }

    /** Same request, same words - so the prompt is cacheable and testable. */
    @Test
    fun theSameRequestBuildsTheSamePrompt() {
        val request = request(context = TranslationContext(bookTitle = "Moby-Dick"))

        assertEquals(buildTranslationPrompt(request), buildTranslationPrompt(request))
    }

    /** The standing instructions must not carry the book, or nothing caches. */
    @Test
    fun theSystemPromptDependsOnlyOnTheLanguage() {
        val one = buildTranslationPrompt(request(context = TranslationContext(bookTitle = "A")))
        val other = buildTranslationPrompt(
            request(text = "Different words.", context = TranslationContext(bookTitle = "B")),
        )

        assertEquals(one.system, other.system)
    }

    @Test
    fun theSystemPromptReadsAsProseRatherThanAsWrappedSource() {
        val system = buildTranslationPrompt(request()).system

        assertFalse(system.contains("  "), "double spaces survived the unwrapping")
        assertTrue(system.contains("\n\n"), "paragraphs were collapsed away")
        assertFalse(system.lines().any { it != it.trim() }, "a line kept its indentation")
    }

    private fun request(
        text: String = "Call me Ishmael.",
        targetLanguage: TargetLanguage = TargetLanguage.Finnish,
        context: TranslationContext = TranslationContext.None,
    ) = TranslationRequest(text = text, targetLanguage = targetLanguage, context = context)
}
