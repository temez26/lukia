package io.lukia.features.translate.domain

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
        val first = translationCacheKey(request(), translator = "Anthropic/claude")
        val second = translationCacheKey(request(), translator = "Anthropic/claude")

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
            key(request(language = TargetLanguage.Spanish))
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
            translationCacheKey(request(), translator = "Anthropic/claude-a"),
            translationCacheKey(request(), translator = "Anthropic/claude-b")
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
            key(request(context = TranslationContext(surroundingText = "She drew her piece.")))
        )
    }

    @Test
    fun aDifferentBookIsADifferentKey() {
        assertNotEquals(
            key(request(context = TranslationContext(bookTitle = "Moby-Dick"))),
            key(request(context = TranslationContext(bookTitle = "Middlemarch")))
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
            key(request(context = TranslationContext(bookTitle = "MobyDick")))
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

    private fun key(request: TranslationRequest): String =
        translationCacheKey(request, translator = "Anthropic/claude").value

    private fun request(
        text: String = passage,
        language: TargetLanguage = TargetLanguage.Finnish,
        context: TranslationContext = TranslationContext.None
    ) = TranslationRequest(text = text, targetLanguage = language, context = context)
}
