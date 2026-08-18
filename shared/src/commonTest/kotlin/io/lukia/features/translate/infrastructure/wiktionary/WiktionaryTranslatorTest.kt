package io.lukia.features.translate.infrastructure.wiktionary

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.lukia.core.domain.AppError
import io.lukia.core.domain.FakeSecretStore
import io.lukia.core.domain.FakeSettingsStore
import io.lukia.core.domain.toAppError
import io.lukia.features.translate.domain.PassageUnit
import io.lukia.features.translate.domain.TargetLanguage
import io.lukia.features.translate.domain.TranslationRequest
import io.lukia.features.translate.domain.TranslationSettings
import io.lukia.features.translate.domain.translate
import io.lukia.features.translate.infrastructure.TranslationSettingsRepository
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The dictionary, which gives the senses a word has rather than choosing one.
 *
 * What is worth proving is the reading of the entry: it comes back grouped by
 * which language the word belongs to and then by part of speech, with the
 * definitions themselves as fragments of HTML - none of which belongs in the
 * middle of a paragraph of a book.
 */
class WiktionaryTranslatorTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun theFirstSenseIsWhatStandsInThePage() = runTest {
        val translator = translator(answers { respondJson(ENTRY) })

        assertEquals("a large marine mammal", translator.translate(request()).text)
    }

    /** The rest go where a translator's remark goes: under the paragraph. */
    @Test
    fun theOtherSensesAreKeptAsARemark() = runTest {
        val translator = translator(answers { respondJson(ENTRY) })

        val note = translator.translate(request()).note.orEmpty()

        assertTrue(note.contains("to hunt whales"), note)
    }

    /**
     * The definitions arrive as HTML - a definition is mostly links to the words
     * in it - and a link spliced into a book is a thing to tap that leads
     * nowhere.
     */
    @Test
    fun theMarkupAroundASenseIsTakenOut() = runTest {
        val translator = translator(answers { respondJson(ENTRY) })

        val text = translator.translate(request()).text

        assertTrue(!text.contains("<"), text)
        assertTrue(!text.contains("href"), text)
    }

    /** The reader's own language first, so the senses come back in it. */
    @Test
    fun theReadersOwnWiktionaryIsAskedFirst() = runTest {
        var sent: HttpRequestData? = null
        val translator = translator(answers { sent = it; respondJson(ENTRY) })

        translator.translate(request())

        assertTrue(sent?.url.toString().startsWith("https://fi.wiktionary.org/"), "$sent")
    }

    /**
     * The smaller Wiktionaries are thin, and a word missing from one is a 404
     * rather than a failure - which is the whole reason there is a second to
     * try.
     */
    @Test
    fun theEnglishWiktionaryIsAskedWhenTheReadersOwnHasNoEntry() = runTest {
        val asked = mutableListOf<String>()
        val translator = translator(
            answers { request ->
                asked += request.url.host

                if (request.url.host.startsWith("fi")) {
                    respondJson("", HttpStatusCode.NotFound)
                } else {
                    respondJson(ENTRY)
                }
            }
        )

        assertEquals("a large marine mammal", translator.translate(request()).text)
        assertEquals(listOf("fi.wiktionary.org", "en.wiktionary.org"), asked)
    }

    @Test
    fun aWordInNoDictionaryAtAllSaysSo() = runTest {
        val translator = translator(answers { respondJson("", HttpStatusCode.NotFound) })

        val error = translator.failure() as AppError.Unexpected

        assertTrue(error.cause.orEmpty().contains("No dictionary entry"), error.cause.orEmpty())
    }

    private fun translator(engine: MockEngine): WiktionaryTranslator {
        val settings = TranslationSettingsRepository(FakeSettingsStore(), FakeSecretStore()).apply {
            update(TranslationSettings(targetLanguage = TargetLanguage.Finnish))
        }

        val client = HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout)
        }

        return WiktionaryTranslator(client, settings, json)
    }

    private fun request(text: String = "whale") = TranslationRequest(
        text = text,
        targetLanguage = TargetLanguage.Finnish,
        unit = PassageUnit.Word
    )

    private suspend fun WiktionaryTranslator.failure(): AppError =
        runCatching { translate(request()) }.exceptionOrNull()?.toAppError()
            ?: error("expected a failure")

    private companion object {

        /**
         * An entry as the service really sends one: keyed by the language the
         * word belongs to, then grouped by part of speech, with each definition
         * a fragment of HTML.
         */
        const val ENTRY = """
            {
              "en": [
                {
                  "partOfSpeech": "Noun",
                  "definitions": [
                    {"definition": "a large <a href=\"/wiki/marine\">marine</a> mammal"}
                  ]
                },
                {
                  "partOfSpeech": "Verb",
                  "definitions": [{"definition": "to hunt whales"}]
                }
              ]
            }
        """
    }
}

private fun answers(
    handle: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
) = MockEngine { request -> handle(request) }

private fun MockRequestHandleScope.respondJson(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK
) = respond(
    content = body,
    status = status,
    headers = headersOf("Content-Type", ContentType.Application.Json.toString())
)
