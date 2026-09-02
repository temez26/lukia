package io.lukia.features.translate.infrastructure.wiktionary

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.lukia.core.domain.AppError
import io.lukia.core.domain.storage.FakeSecretStore
import io.lukia.core.domain.storage.FakeSettingsStore
import io.lukia.core.domain.toAppError
import io.lukia.features.translate.domain.settings.TargetLanguage
import io.lukia.features.translate.domain.settings.TranslationSettings
import io.lukia.features.translate.infrastructure.TranslationSettingsRepository
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The whole entry, as opposed to the line of it that fits over a word.
 *
 * The same pages [WiktionaryTranslator] reads and a different amount of them,
 * which is the only thing separating the two - so what is worth holding here is
 * that the entry is not cut to the three senses the reader sees in the book.
 */
class WiktionaryDictionaryTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun everySenseTheEntryHasComesBack() = runTest {
        val entry = dictionary(answers(MANY_SENSES)).define("set", TargetLanguage.Finnish)

        assertEquals(
            listOf("asettaa", "laittaa", "sarja", "joukko", "kiinteä"),
            entry.senses,
        )
    }

    /** More than the three a word in a paragraph can carry - see WiktionaryTranslator. */
    @Test
    fun theEntryIsNotCutToWhatFitsOverAWord() = runTest {
        val entry = dictionary(answers(MANY_SENSES)).define("set", TargetLanguage.Finnish)

        assertTrue(entry.senses.size > 3, "the entry was cut to ${entry.senses.size} senses")
    }

    @Test
    fun theWordItIsAboutComesBackWithIt() = runTest {
        val entry = dictionary(answers(MANY_SENSES)).define("  set  ", TargetLanguage.Finnish)

        assertEquals("set", entry.word)
    }

    /**
     * The case that made the dictionary useless on real prose. A book is written
     * in inflected words, and the Finnish Wiktionary's page for `values` is not a
     * definition at all - it says `monikkomuoto sanasta value`, which tells
     * somebody who wanted `arvo` only that they have more looking up to do.
     */
    @Test
    fun aPageThatOnlySaysWhichWordThisIsAFormOfFollowsToThatWord() = runTest {
        val asked = mutableListOf<String>()
        val dictionary = dictionary(
            answering { request ->
                asked += request.url.parameters["titles"].orEmpty()

                if (request.url.parameters["titles"] == "values") FORM_OF else VALUE_ENTRY
            },
        )

        val entry = dictionary.define("values", TargetLanguage.Finnish)

        assertEquals(listOf("arvo", "hinta"), entry.senses)
        assertEquals(listOf("values", "value"), asked)
    }

    /** And it says which word it answered under, since that is not the one asked. */
    @Test
    fun theWordItAnsweredUnderComesBackWithIt() = runTest {
        val dictionary = dictionary(
            answering { request ->
                if (request.url.parameters["titles"] == "values") FORM_OF else VALUE_ENTRY
            },
        )

        assertEquals("value", dictionary.define("values", TargetLanguage.Finnish).word)
    }

    /**
     * Followed once and no further, and only when there is something at the other
     * end: a base form with no entry of its own leaves the word's own page
     * standing, which is a poor answer and better than none.
     */
    @Test
    fun aBaseFormWithNoEntryLeavesWhatTheWordsOwnPageSaid() = runTest {
        val dictionary = dictionary(
            answering { request ->
                if (request.url.parameters["titles"] == "values") FORM_OF else MISSING_PAGE
            },
        )

        val entry = dictionary.define("values", TargetLanguage.Finnish)

        assertEquals("values", entry.word)
        assertEquals(listOf("monikkomuoto sanasta value"), entry.senses)
    }

    /**
     * A definition is not a pointer. Following the last word of every two-word
     * sense would send a reader from `value` to `hinta`.
     */
    @Test
    fun anEntryThatDefinesTheWordIsNotFollowedAnywhere() = runTest {
        val asked = mutableListOf<String>()
        val dictionary = dictionary(
            answering { request ->
                asked += request.url.parameters["titles"].orEmpty()
                VALUE_ENTRY
            },
        )

        assertEquals("value", dictionary.define("value", TargetLanguage.Finnish).word)
        assertEquals(listOf("value"), asked)
    }

    @Test
    fun aWordInNoDictionaryAtAllSaysSo() = runTest {
        val failure = runCatching {
            dictionary(answers(MISSING_PAGE)).define("set", TargetLanguage.English)
        }.exceptionOrNull()?.toAppError() as? AppError.Unexpected

        assertTrue(
            failure?.cause.orEmpty().contains("No dictionary entry"),
            failure?.cause.orEmpty(),
        )
    }

    private fun dictionary(engine: MockEngine): WiktionaryDictionary {
        val settings = TranslationSettingsRepository(FakeSettingsStore(), FakeSecretStore()).apply {
            update(TranslationSettings(targetLanguage = TargetLanguage.Finnish))
        }

        val client = HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout)
        }

        return WiktionaryDictionary(WiktionaryPages(client, settings, json))
    }

    private fun answers(body: String) = answering { body }

    private fun answering(body: (HttpRequestData) -> String) = MockEngine { request ->
        respond(
            content = body(request),
            headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
        )
    }

    private companion object {

        /** One language's section with more senses than fit over a word. */
        const val MANY_SENSES = """
            {"query": {"pages": [{"title": "set", "extract":
            "== Englanti ==\n=== Verbi ===\nasettaa\nlaittaa\nsarja\njoukko\nkiinteä\n"}]}}
        """

        const val MISSING_PAGE = """{"query": {"pages": [{"title": "set", "missing": true}]}}"""

        /** A page that is only a pointer, as a wiki really writes one. */
        const val FORM_OF = """
            {"query": {"pages": [{"title": "values", "extract":
            "== Englanti ==\n=== Substantiivi ===\nmonikkomuoto sanasta value\n"}]}}
        """

        const val VALUE_ENTRY = """
            {"query": {"pages": [{"title": "value", "extract":
            "== Englanti ==\n=== Substantiivi ===\narvo\nhinta\n"}]}}
        """
    }
}
