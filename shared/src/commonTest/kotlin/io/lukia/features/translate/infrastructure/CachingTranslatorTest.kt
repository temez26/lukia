package io.lukia.features.translate.infrastructure

import io.lukia.features.translate.domain.CacheIdentity
import io.lukia.features.translate.domain.Translation
import io.lukia.features.translate.domain.TranslationCache
import io.lukia.features.translate.domain.TranslationCacheKey
import io.lukia.features.translate.domain.TranslationContext
import io.lukia.features.translate.domain.TranslationRequest
import io.lukia.features.translate.domain.Translator
import io.lukia.features.translate.domain.settings.TargetLanguage
import io.lukia.features.translate.domain.translate
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
            translator = { _, _ -> Translation("Kutsukaa minua.", note = "A remark about a word.") },
            cache = cache,
            answering = { CacheIdentity("Anthropic/claude", readsTheBook = true) },
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
            translator = { request, _ ->
                asked += request.text
                Translation("Kutsukaa minua Ismaeliksi.")
            },
            cache = cache,
            answering = { CacheIdentity("Anthropic/$model", readsTheBook = true) },
        )

        translator.translate(request())
        model = "claude-b"
        translator.translate(request())

        assertEquals(2, asked.size)
    }

    /**
     * One word, paid for once, wherever it is read.
     *
     * A dictionary and Google are handed the word and the language and nothing
     * else - so `values` in Moby-Dick and `values` in Middlemarch are not two
     * questions, and keying them by the book made them two lookups with one
     * answer. Which on a language that inflects everything is most of the words
     * in the book, over again in every book.
     */
    @Test
    fun aWordAnsweredWithoutTheBookIsAskedOnceForEveryBook() = runTest {
        val asked = mutableListOf<String>()

        val translator = CachingTranslator(
            translator = { request, _ ->
                asked += request.text
                Translation("arvo")
            },
            cache = InMemoryCache(),
            answering = { CacheIdentity("word/Wiktionary", readsTheBook = false) },
        )

        translator.translate(request(text = "values", book = "Moby-Dick"))
        val second = translator.translate(request(text = "values", book = "Middlemarch"))

        assertEquals("arvo", second.text)
        assertEquals(listOf("values"), asked)
    }

    /** And the paragraph it sits in does not make it a second word either. */
    @Test
    fun aWordAnsweredWithoutTheBookIgnoresTheParagraphAroundIt() = runTest {
        val asked = mutableListOf<String>()

        val translator = CachingTranslator(
            translator = { request, _ ->
                asked += request.text
                Translation("arvo")
            },
            cache = InMemoryCache(),
            answering = { CacheIdentity("word/Wiktionary", readsTheBook = false) },
        )

        translator.translate(request(text = "values", around = "He drew his piece."))
        translator.translate(request(text = "values", around = "She left at noon."))

        assertEquals(listOf("values"), asked)
    }

    /**
     * A model is the other way round, and deliberately: it is handed the
     * paragraph and uses it, so the same word in another one is another
     * question and has to be asked again.
     */
    @Test
    fun aPassageAnsweredWithTheBookIsAskedAgainInAnother() = runTest {
        val asked = mutableListOf<String>()

        val translator = CachingTranslator(
            translator = { request, _ ->
                asked += request.text
                Translation("ase")
            },
            cache = InMemoryCache(),
            answering = { CacheIdentity("Anthropic/claude", readsTheBook = true) },
        )

        translator.translate(request(text = "piece", around = "He drew his piece."))
        translator.translate(request(text = "piece", around = "She played a piece."))

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
            translator = { _, _ ->
                attempt++
                if (attempt == 1) throw RuntimeException("no network") else Translation("Second time.")
            },
            cache = cache,
            answering = { CacheIdentity("Anthropic/claude", readsTheBook = true) },
        )

        assertFailsWith<RuntimeException> { translator.translate(request()) }

        assertEquals("Second time.", translator.translate(request()).text)
        assertTrue(cache.stored.isNotEmpty(), "the second answer was not kept either")
    }

    private fun caching(asked: MutableList<String>): Translator = CachingTranslator(
        translator = { request, _ ->
            asked += request.text
            Translation("Kutsukaa minua Ismaeliksi.")
        },
        cache = InMemoryCache(),
        answering = { CacheIdentity("Anthropic/claude", readsTheBook = true) },
    )

    private fun request(
        text: String = passage,
        book: String = "Moby-Dick",
        around: String? = null,
    ) = TranslationRequest(
        text = text,
        targetLanguage = TargetLanguage.Finnish,
        context = TranslationContext(bookTitle = book, surroundingText = around),
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
