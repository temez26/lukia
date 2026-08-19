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
 * The case that matters most is which wiki is asked. An earlier version reached
 * for Wikimedia's REST definition endpoint, which exists only on the English
 * Wiktionary - so every other wiki answered a 404 and every lookup fell through
 * to English, whatever language the reader had chosen.
 */
class WiktionaryTranslatorTest {

    private val json = Json { ignoreUnknownKeys = true }

    /** The whole point: a Finnish reader is asking the Finnish Wiktionary. */
    @Test
    fun theReadersOwnWiktionaryIsAskedFirst() = runTest {
        var sent: HttpRequestData? = null
        val translator = translator(answers { sent = it; respondJson(FINNISH_ENTRY) })

        translator.translate(request())

        assertEquals("fi.wiktionary.org", sent?.url?.host)
    }

    @Test
    fun theSenseComesBackInTheReadersOwnLanguage() = runTest {
        val translator = translator(answers { respondJson(FINNISH_ENTRY) })

        assertEquals("valas", translator.translate(request()).text)
    }

    /** The headings are the scaffolding, not the meaning. */
    @Test
    fun theHeadingsOfAnEntryAreNotSenses() = runTest {
        val translator = translator(answers { respondJson(FINNISH_ENTRY) })

        val translation = translator.translate(request())

        assertTrue(!translation.text.contains("="), translation.text)
        assertTrue(!translation.text.contains("Substantiivi"), translation.text)
    }

    /** The rest of them go under the paragraph, as a translator's remark does. */
    @Test
    fun theOtherSensesAreKeptAsARemark() = runTest {
        val translator = translator(answers { respondJson(FINNISH_ENTRY) })

        assertTrue(translator.translate(request()).note.orEmpty().contains("valaskala"))
    }


    /**
     * The page that prompted all of this. An entry opens with its headword and
     * inflections, and lists its derived terms after the senses - and taking the
     * first few lines put `support (monikko supports)` on the page in place of
     * the sentence, with the actual word left as a footnote under it.
     */
    @Test
    fun theHeadwordAndItsInflectionsAreNotASense() = runTest {
        val translator = translator(answers { respondJson(SUPPORT_ENTRY) })

        val translation = translator.translate(request(text = "support"))

        assertEquals("kannatus", translation.text)
    }

    @Test
    fun theTermsBuiltOnAWordAreNotSensesOfIt() = runTest {
        val translator = translator(answers { respondJson(SUPPORT_ENTRY) })

        val note = translator.translate(request(text = "support")).note.orEmpty()

        assertTrue(!note.contains("support in decline"), note)
    }

    /**
     * A word spelled the same way in three languages has three sections, and one
     * sense from each is not an answer to anything.
     */
    @Test
    fun theSensesStopAtTheNextHeading() = runTest {
        val translator = translator(answers { respondJson(SUPPORT_ENTRY) })

        val translation = translator.translate(request(text = "support"))

        assertTrue(!translation.note.orEmpty().contains("soutien"), translation.note.orEmpty())
    }

    /** A longer word starting the same way is a different word, not scaffolding. */
    @Test
    fun aLongerWordBeginningTheSameWayIsStillASense() = runTest {
        val entry = """{"query": {"pages": [{"title": "set", "extract":
            "== Englanti ==
=== Verbi ===
set
settled asia
"}]}}"""
        val translator = translator(answers { respondJson(entry) })

        assertEquals("settled asia", translator.translate(request(text = "set")).text)
    }

    /**
     * An entry that is nothing but its own headword - a loanword, a name - is a
     * poor answer and a better one than telling the reader there is no entry
     * when the page is right in front of them.
     */
    @Test
    fun anEntryThatIsOnlyItsHeadwordIsStillAnAnswer() = runTest {
        val entry = """{"query": {"pages": [{"title": "sauna", "extract":
            "== Suomi ==
=== Substantiivi ===
sauna
"}]}}"""
        val translator = translator(answers { respondJson(entry) })

        assertEquals("sauna", translator.translate(request(text = "sauna")).text)
    }

    /**
     * The smaller Wiktionaries are thin, and a word missing from one is an
     * answer rather than a failure - which is the whole reason there is a
     * second to try.
     */
    @Test
    fun theEnglishWiktionaryIsAskedWhenTheReadersOwnHasNoPage() = runTest {
        val asked = mutableListOf<String>()
        val translator = translator(
            answers { request ->
                asked += request.url.host

                if (request.url.host.startsWith("fi")) {
                    respondJson(MISSING_PAGE)
                } else {
                    respondJson(ENGLISH_ENTRY)
                }
            }
        )

        assertEquals("a large marine mammal", translator.translate(request()).text)
        assertEquals(listOf("fi.wiktionary.org", "en.wiktionary.org"), asked)
    }

    /** An English reader has only one wiki to ask, and it is not asked twice. */
    @Test
    fun anEnglishReaderIsNotAskedTheSameWikiTwice() = runTest {
        val asked = mutableListOf<String>()
        val translator = translator(
            answers { request -> asked += request.url.host; respondJson(MISSING_PAGE) },
            language = TargetLanguage.English
        )

        translator.failure(request(language = TargetLanguage.English))

        assertEquals(listOf("en.wiktionary.org"), asked)
    }

    @Test
    fun aWordInNoDictionaryAtAllSaysSo() = runTest {
        val translator = translator(answers { respondJson(MISSING_PAGE) })

        val error = translator.failure() as AppError.Unexpected

        assertTrue(error.cause.orEmpty().contains("No dictionary entry"), error.cause.orEmpty())
    }

    private fun translator(
        engine: MockEngine,
        language: TargetLanguage = TargetLanguage.Finnish
    ): WiktionaryTranslator {
        val settings = TranslationSettingsRepository(FakeSettingsStore(), FakeSecretStore()).apply {
            update(TranslationSettings(targetLanguage = language))
        }

        val client = HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout)
        }

        return WiktionaryTranslator(client, settings, json)
    }

    private fun request(
        text: String = "whale",
        language: TargetLanguage = TargetLanguage.Finnish
    ) = TranslationRequest(text = text, targetLanguage = language, unit = PassageUnit.Word)

    private suspend fun WiktionaryTranslator.failure(
        request: TranslationRequest = request()
    ): AppError = runCatching { translate(request) }.exceptionOrNull()?.toAppError()
        ?: error("expected a failure")

    private companion object {

        /**
         * A page as the query API really hands one over: the whole entry as one
         * run of text, with its headings still wrapped in equals signs and its
         * senses on the lines between them.
         */
        const val FINNISH_ENTRY = """
            {"query": {"pages": [{"title": "whale", "extract":
            "== Englanti ==\n=== Substantiivi ===\nvalas\nvalaskala\n"}]}}
        """

        const val ENGLISH_ENTRY = """
            {"query": {"pages": [{"title": "whale", "extract":
            "== English ==\n=== Noun ===\na large marine mammal\n"}]}}
        """

        /**
         * A real entry's shape: the headword and its inflections, then the
         * senses, then the terms built on it, then another language entirely.
         */
        const val SUPPORT_ENTRY = """
            {"query": {"pages": [{"title": "support", "extract":
            "== Englanti ==
=== Substantiivi ===
support (monikko supports)
kannatus
tuki

            ==== Johdokset ====
support in decline
== Ranska ==
soutien
"}]}}
        """

        /** A wiki says "there is no such page" rather than failing to answer. */
        const val MISSING_PAGE = """{"query": {"pages": [{"title": "whale", "missing": true}]}}"""
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
