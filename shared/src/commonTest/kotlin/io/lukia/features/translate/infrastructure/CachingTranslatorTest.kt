package io.lukia.features.translate.infrastructure

import io.lukia.features.translate.domain.TargetLanguage
import io.lukia.features.translate.domain.Translation
import io.lukia.features.translate.domain.TranslationCache
import io.lukia.features.translate.domain.TranslationCacheKey
import io.lukia.features.translate.domain.TranslationContext
import io.lukia.features.translate.domain.TranslationRequest
import io.lukia.features.translate.domain.Translator
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CachingTranslatorTest {

    private val passage = "Call me Ishmael."

    @Test
    fun aPassageNobodyHasAskedAboutIsTranslated() = runTest {
        val asked = mutableListOf<String>()
        val translator = caching(asked)

        assertEquals("Kutsukaa minua Ismaeliksi.", translator.translate(request()).text)
        assertEquals(listOf(passage), asked)
    }

    /** The saving this whole thing exists for. */
    @Test
    fun thesamePassageAskedTwiceIsOnlySentOnce() = runTest {
        val asked = mutableListOf<String>()
        val translator = caching(asked)

        translator.translate(request())
        val second = translator.translate(request())

        assertEquals("Kutsukaa minua Ismaeliksi.", second.text)
        assertEquals(1, asked.size)
    }

    /** A remark about a word is part of the answer, and has to come back with it. */
    @Test
    fun theNoteIsRememberedAlongsideTheTranslation() = runTest {
        val cache = InMemoryCache()
        val translator = CachingTranslator(
            translator = { Translation("Kutsukaa minua.", note = "A remark about a word.") },
            cache = cache,
            translatorIdentity = { "Anthropic/claude" }
        )

        translator.translate(request())

        assertEquals("A remark about a word.", translator.translate(request()).note)
    }

    /** Switching model is how somebody compares two of them. */
    @Test
    fun aChangeOfModelIsAskedAgain() = runTest {
        val asked = mutableListOf<String>()
        val cache = InMemoryCache()
        var model = "claude-a"

        val translator = CachingTranslator(
            translator = { asked += it.text; Translation("Kutsukaa minua Ismaeliksi.") },
            cache = cache,
            translatorIdentity = { "Anthropic/$model" }
        )

        translator.translate(request())
        model = "claude-b"
        translator.translate(request())

        assertEquals(2, asked.size)
    }

    @Test
    fun anotherPassageIsAskedAbout() = runTest {
        val asked = mutableListOf<String>()
        val translator = caching(asked)

        translator.translate(request())
        translator.translate(request(text = "Some years ago."))

        assertEquals(listOf(passage, "Some years ago."), asked)
    }

    /**
     * What fails here is a key, a network, or a service having a bad minute.
     * A remembered failure would be a sentence that could never be translated
     * again without clearing storage.
     */
    @Test
    fun aFailureIsNotRemembered() = runTest {
        val cache = InMemoryCache()
        var attempt = 0

        val translator = CachingTranslator(
            translator = {
                attempt++
                if (attempt == 1) throw RuntimeException("no network") else Translation("Second time.")
            },
            cache = cache,
            translatorIdentity = { "Anthropic/claude" }
        )

        assertFailsWith<RuntimeException> { translator.translate(request()) }

        assertEquals("Second time.", translator.translate(request()).text)
        assertTrue(cache.stored.isNotEmpty(), "the second answer was not kept either")
    }

    private fun caching(asked: MutableList<String>): Translator = CachingTranslator(
        translator = { asked += it.text; Translation("Kutsukaa minua Ismaeliksi.") },
        cache = InMemoryCache(),
        translatorIdentity = { "Anthropic/claude" }
    )

    private fun request(text: String = passage) = TranslationRequest(
        text = text,
        targetLanguage = TargetLanguage.Finnish,
        context = TranslationContext(bookTitle = "Moby-Dick")
    )

    /** What is stored is the point here, not how. See `SqlTranslationCacheTest`. */
    private class InMemoryCache : TranslationCache {

        val stored = mutableMapOf<String, Translation>()

        override suspend fun get(key: TranslationCacheKey): Translation? = stored[key.value]

        override suspend fun put(key: TranslationCacheKey, translation: Translation) {
            stored[key.value] = translation
        }
    }
}
