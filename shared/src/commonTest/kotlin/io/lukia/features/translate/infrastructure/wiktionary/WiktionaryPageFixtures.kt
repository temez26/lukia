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
import io.lukia.core.domain.storage.FakeSecretStore
import io.lukia.core.domain.storage.FakeSettingsStore
import io.lukia.features.translate.domain.settings.TargetLanguage
import io.lukia.features.translate.domain.settings.TranslationSettings
import io.lukia.features.translate.infrastructure.TranslationSettingsRepository
import kotlinx.serialization.json.Json

/**
 * Wiktionary pages as the query API really hands them over, and the dictionary
 * that reads them.
 *
 * Shared by the two things worth testing separately - what an entry comes back
 * as, and which word an entry is followed to - because the pages that show one
 * are usually the pages that show the other. `skills` is a pointer to be
 * followed and a page with a pronunciation on top of it at the same time.
 */
internal fun wiktionaryDictionary(
    engine: MockEngine,
    language: TargetLanguage = TargetLanguage.Finnish,
    cache: WiktionaryPageCache = RememberingPages(),
): WiktionaryDictionary {
    val json = Json { ignoreUnknownKeys = true }

    val settings = TranslationSettingsRepository(FakeSettingsStore(), FakeSecretStore()).apply {
        update(TranslationSettings(targetLanguage = language))
    }

    val client = HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout)
    }

    return WiktionaryDictionary(WiktionaryPages(client, settings, cache, json = json))
}

/**
 * The page cache these tests run against, which remembers everything and trims
 * nothing.
 *
 * A real one for the tests that are not about caching, so that what they see is
 * what the app does - and a countable one for the tests that are.
 */
internal class RememberingPages : WiktionaryPageCache {

    private val pages = mutableMapOf<Pair<String, String>, CachedPage>()

    override suspend fun get(wiki: String, title: String): CachedPage? = pages[wiki to title]

    override suspend fun put(wiki: String, title: String, extract: String?) {
        pages[wiki to title] = extract?.let { CachedPage.Text(it) } ?: CachedPage.NoSuchPage
    }
}

/** The same page whatever is asked for. */
internal fun respondingWith(body: String) = responding { body }

/** A page per request, for the lookups that ask about more than one word. */
internal fun responding(body: (HttpRequestData) -> String) = MockEngine { request ->
    respond(
        content = body(request),
        headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
    )
}

/** One language's section with more senses than fit over a word. */
internal const val MANY_SENSES = """
    {"query": {"pages": [{"title": "set", "extract":
    "== Englanti ==\n=== Verbi ===\nasettaa\nlaittaa\nsarja\njoukko\nkiinteä\n"}]}}
"""

/** A wiki says "there is no such page" rather than failing to answer. */
internal const val MISSING_PAGE = """{"query": {"pages": [{"title": "set", "missing": true}]}}"""

/** A page that is only a pointer, as a wiki really writes one. */
internal const val FORM_OF = """
    {"query": {"pages": [{"title": "values", "extract":
    "== Englanti ==\n=== Substantiivi ===\nmonikkomuoto sanasta value\n"}]}}
"""

internal const val VALUE_ENTRY = """
    {"query": {"pages": [{"title": "value", "extract":
    "== Englanti ==\n=== Substantiivi ===\narvo\nhinta\n"}]}}
"""

/** A form nothing about its spelling could connect to the word it belongs to. */
internal const val INFLECTION = """
    {"query": {"pages": [{"title": "juoksi", "extract":
    "== Suomi ==\n=== Verbi ===\njuoksi\n
(taivutusmuoto) indikatiivin imperfektin yksikön 3. persoonan muoto verbistä juosta\n"}]}}
"""

internal const val RUN_ENTRY = """
    {"query": {"pages": [{"title": "juosta", "extract":
    "== Suomi ==\n=== Verbi ===\njuosta\nliikkua nopeasti jaloin\n"}]}}
"""

/** How the English Wiktionary writes one, pronunciation and all. */
internal const val PLURAL_OF = """
    {"query": {"pages": [{"title": "skills", "extract":
    "== English ==\n=== Pronunciation ===\nIPA(key): /skɪlz/\n
=== Noun ===\nskills\nplural of skill\n"}]}}
"""

/**
 * One word that is two, with the words it is related to under the first of them
 * and another language beside them both.
 */
internal const val SKILL_ENTRY = """
    {"query": {"pages": [{"title": "skill", "extract":
    "== Englanti ==\n=== Substantiivi ===\nskill (monikko skills)\ntaito, kyky, kyvykkyys\n
==== Liittyvät sanat ====\ntalent\nskilled\n===== Sanaliitot =====\nmotor skill\n
=== Verbi ===\nskill\nosata\n
== Ranska ==\n=== Substantiivi ===\nadresse\n"}]}}
"""

/** A page that exists only under the spelling the book did not use. */
internal const val TALO_ENTRY = """
    {"query": {"pages": [{"title": "talo", "extract":
    "== Suomi ==\n=== Substantiivi ===\ntalo\nasuinrakennus\n"}]}}
"""

/**
 * One word that is four, as the Finnish Wiktionary really writes it: a part of
 * speech at a time, with another language after them.
 */
internal const val MEAN_ENTRY = """
    {"query": {"pages": [{"title": "mean", "extract":
    "== Englanti ==\n=== Adjektiivi ===\n mean (komparatiivi meaner)\nilkeä\n
==== Liittyvät sanat ====\nunkind\n=== Substantiivi ===\nmean (monikko means)\n
(matematiikka, tilastotiede) keskiarvo\n=== Verbi ===\nmean\ntarkoittaa, merkitä\n
==== Ääntäminen ====\nIPA: /miːn/\n
== Espanja ==\n=== Verbi ===\nmean\nmonikon 3. persoonan muoto verbistä mear\n"}]}}
"""

/** A definition that happens to be a phrase, which is not a pointer at its last word. */
internal const val PLOT_ENTRY = """
    {"query": {"pages": [{"title": "plot", "extract":
    "== English ==\n=== Noun ===\na piece of land\n"}]}}
"""
