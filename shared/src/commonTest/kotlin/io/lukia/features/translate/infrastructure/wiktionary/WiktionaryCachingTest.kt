package io.lukia.features.translate.infrastructure.wiktionary

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.lukia.features.translate.domain.settings.TargetLanguage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Asking a wiki once for what it has already said.
 *
 * One word is not one request. The reader's own wiki is asked and then the
 * English one, each under the word as the book spelled it and again in lower
 * case, and a word that turns out to be a form of another is all of that a
 * second time for the word it points at. A reader who opens the same word's
 * panel twice was making every one of those twice - and Wikimedia limits how
 * often it will answer a client with no key, so the second reader of a busy page
 * was being refused rather than merely kept waiting.
 */
class WiktionaryCachingTest {

    @Test
    fun aWordLookedUpTwiceReachesTheWikiOnce() = runTest {
        val asked = mutableListOf<String>()
        val cache = RememberingPages()

        val dictionary = wiktionaryDictionary(
            responding { request ->
                asked += request.url.parameters["titles"].orEmpty()
                MANY_SENSES
            },
            cache = cache,
        )

        dictionary.define("set", TargetLanguage.Finnish)
        dictionary.define("set", TargetLanguage.Finnish)

        assertEquals(listOf("set"), asked)
    }

    /**
     * Including the word it was followed to. Both pages behind an inflected word
     * are kept, so the second reading of `values` is no requests at all rather
     * than the two the first one cost.
     */
    @Test
    fun theWordAFormWasFollowedToIsKeptAsWell() = runTest {
        val asked = mutableListOf<String>()

        val dictionary = wiktionaryDictionary(
            responding { request ->
                asked += request.url.parameters["titles"].orEmpty()

                if (request.url.parameters["titles"] == "values") FORM_OF else VALUE_ENTRY
            },
            cache = RememberingPages(),
        )

        dictionary.define("values", TargetLanguage.Finnish)
        val second = dictionary.define("values", TargetLanguage.Finnish)

        assertEquals(listOf("arvo", "hinta"), second.senses)
        assertEquals(listOf("values", "value"), asked)
    }

    /**
     * And a wiki having no page is remembered too, which is the answer that
     * costs the most to forget: a word missing from the reader's own wiki is
     * looked up on the English one as well, so re-asking a miss is two requests.
     */
    @Test
    fun aWikiWithNoPageForTheWordIsNotAskedAgain() = runTest {
        val asked = mutableListOf<String>()

        val dictionary = wiktionaryDictionary(
            responding { request ->
                asked += "${request.url.host}/${request.url.parameters["titles"]}"

                if (request.url.parameters["titles"] == "talo") TALO_ENTRY else MISSING_PAGE
            },
            cache = RememberingPages(),
        )

        dictionary.define("Talo", TargetLanguage.Finnish)
        dictionary.define("Talo", TargetLanguage.Finnish)

        assertEquals(listOf("fi.wiktionary.org/Talo", "fi.wiktionary.org/talo"), asked)
    }

    /**
     * A request that fails outright is not remembered. What fails there is a
     * network or a wiki having a bad minute, and a remembered failure would be a
     * word that could never be looked up again without clearing storage.
     */
    @Test
    fun aWikiThatCouldNotAnswerIsAskedAgain() = runTest {
        var attempts = 0

        val dictionary = wiktionaryDictionary(
            refusing { attempts++ },
            cache = RememberingPages(),
        )

        runCatching { dictionary.define("set", TargetLanguage.English) }
        runCatching { dictionary.define("set", TargetLanguage.English) }

        assertEquals(2, attempts)
    }

    /** A wiki that will not answer, counting how often it was asked. */
    private fun refusing(onRequest: () -> Unit) = MockEngine {
        onRequest()
        respondError(HttpStatusCode.TooManyRequests)
    }
}
