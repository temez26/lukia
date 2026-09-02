package io.lukia.features.translate.domain

import io.lukia.features.translate.domain.settings.TargetLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * The key decides what the cache is allowed to answer without asking anybody.
 *
 * Every one of these is the same shape of mistake: two questions that are not
 * the same question sharing a key, and a reader being handed the answer to the
 * other one. So each thing that changes the answer gets a test saying it changes
 * the key.
 */
class TranslationCacheKeyTest {

    private val passage = "Call me Ishmael."

    @Test
    fun theSamePassageAskedTheSameWayIsTheSameKey() {
        assertEquals(key(request()), key(request()))
    }

    /** The whole point: a hash written to disk has to mean the same next time. */
    @Test
    fun theKeyIsMadeOfNothingButWhatIsPassedIn() {
        val first = translationCacheKey(request(), reading("Anthropic/claude"))
        val second = translationCacheKey(request(), reading("Anthropic/claude"))

        assertEquals(first.value, second.value)
    }

    @Test
    fun adifferentPassageIsADifferentKey() {
        assertNotEquals(key(request()), key(request(text = "Some years ago.")))
    }

    @Test
    fun theSamePassageInAnotherLanguageIsADifferentKey() {
        assertNotEquals(
            key(request()),
            key(request(language = TargetLanguage.Spanish)),
        )
    }

    /**
     * Two models are two answers, and comparing them is a thing readers do
     * deliberately. A cache that ignored this would show them one answer twice
     * and prove nothing.
     */
    @Test
    fun anotherModelIsADifferentKey() {
        assertNotEquals(
            translationCacheKey(request(), reading("Anthropic/claude-a")),
            translationCacheKey(request(), reading("Anthropic/claude-b")),
        )
    }

    /**
     * The paragraph around a sentence is what settles which sense of a word was
     * meant, so the same words in another paragraph are another question.
     */
    @Test
    fun theSameSentenceInDifferentSurroundingsIsADifferentKey() {
        assertNotEquals(
            key(request(context = TranslationContext(surroundingText = "He drew his piece."))),
            key(request(context = TranslationContext(surroundingText = "She drew her piece."))),
        )
    }

    @Test
    fun aDifferentBookIsADifferentKey() {
        assertNotEquals(
            key(request(context = TranslationContext(bookTitle = "Moby-Dick"))),
            key(request(context = TranslationContext(bookTitle = "Middlemarch"))),
        )
    }

    /**
     * The fields are separated, so no arrangement of one field's contents can
     * be read as the next field having begun.
     */
    @Test
    fun aFieldCannotSpillIntoTheOneAfterIt() {
        assertNotEquals(
            key(request(context = TranslationContext(bookTitle = "Moby", chapterTitle = "Dick"))),
            key(request(context = TranslationContext(bookTitle = "MobyDick"))),
        )
    }

    /** Whitespace either side of a passage is not part of the passage. */
    @Test
    fun aPassageIsTrimmedBeforeItIsHashed() {
        assertEquals(key(request()), key(request(text = "  $passage\n")))
    }

    @Test
    fun theKeyIsTheTwoHashesItIsMadeOf() {
        assertEquals(32, key(request()).length)
    }

    /**
     * The word looked up where the book cannot change the answer - a dictionary
     * or Google, which are handed the word and the language and nothing else.
     */
    @Test
    fun aWordLookedUpWithoutTheBookIsTheSameKeyInEveryBook() {
        val here = TranslationContext(bookTitle = "Moby-Dick", surroundingText = "He drew his piece.")
        val there = TranslationContext(bookTitle = "Middlemarch", surroundingText = "She left.")

        assertEquals(
            translationCacheKey(request(context = here), blind("word/Wiktionary")),
            translationCacheKey(request(context = there), blind("word/Wiktionary")),
        )
    }

    /** And it is still not the same key as another word, or another language. */
    @Test
    fun aWordLookedUpWithoutTheBookIsStillItsOwnWord() {
        assertNotEquals(
            translationCacheKey(request(), blind("word/Wiktionary")),
            translationCacheKey(request(text = "whale"), blind("word/Wiktionary")),
        )

        assertNotEquals(
            translationCacheKey(request(), blind("word/Wiktionary")),
            translationCacheKey(request(language = TargetLanguage.Spanish), blind("word/Wiktionary")),
        )
    }

    /**
     * And a dictionary's answer is not handed to a model, which was asked a
     * different question and read the paragraph to answer it.
     */
    @Test
    fun theSameWordAskedOfTwoServicesIsTwoKeys() {
        assertNotEquals(
            translationCacheKey(request(), blind("word/Wiktionary")),
            translationCacheKey(request(), reading("word/Google")),
        )
    }

    private fun key(request: TranslationRequest): String =
        translationCacheKey(request, reading("Anthropic/claude")).value

    /** Someone who is handed the book and uses it. */
    private fun reading(translator: String) = CacheIdentity(translator, readsTheBook = true)

    /** Someone who is handed the word and nothing else. */
    private fun blind(translator: String) = CacheIdentity(translator, readsTheBook = false)

    private fun request(
        text: String = passage,
        language: TargetLanguage = TargetLanguage.Finnish,
        context: TranslationContext = TranslationContext.None,
    ) = TranslationRequest(text = text, targetLanguage = language, context = context)
}
