package io.lukia.features.translate.infrastructure.wiktionary

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
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

    private fun answers(body: String) = MockEngine {
        respond(
            content = body,
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
    }
}
