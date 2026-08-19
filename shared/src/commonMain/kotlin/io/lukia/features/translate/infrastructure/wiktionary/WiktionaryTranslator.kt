package io.lukia.features.translate.infrastructure.wiktionary

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.lukia.core.domain.AppError
import io.lukia.core.domain.AppErrorException
import io.lukia.features.translate.domain.Translation
import io.lukia.features.translate.domain.TranslationRequest
import io.lukia.features.translate.domain.Translator
import io.lukia.features.translate.infrastructure.TranslationSettingsRepository
import io.lukia.features.translate.infrastructure.asTranslationFailure
import io.lukia.features.translate.infrastructure.translationTimeouts
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * How many senses of a word are worth putting in front of somebody who is
 * reading.
 *
 * A dictionary's whole advantage over a translator is that it does not choose,
 * and its whole risk is that it does not choose: a common word has a dozen
 * entries, and a dozen entries spliced into a sentence is not something anybody
 * can read. The first few answer most of the time.
 */
private const val MAX_SENSES = 3

/** What is read when the reader's own language has no entry for the word. */
private const val FALLBACK_WIKI = "en"

/**
 * Looks a word up in Wiktionary, in the language the reader is reading into.
 *
 * A dictionary rather than a translator, which is the point of offering it: it
 * gives the senses a word has instead of choosing one, which is often what
 * somebody learning a language wants from a word they do not know.
 *
 * Asked of the reader's own Wiktionary - a Finnish reader is asking
 * `fi.wiktionary.org`, whose entry for an English word is written in Finnish -
 * and only of the English one when that has no page at all. That fallback is
 * real and will fire: the smaller Wiktionaries are far thinner than the English
 * one, and a word missing from a reader's own leaves them with an English
 * definition, which is worse than their language and better than nothing.
 *
 * Read through the query API rather than the REST one, which is the whole reason
 * this ever answered in English. Wikimedia's `page/definition` endpoint is neat,
 * returns senses already parsed, and exists only on the English Wiktionary -
 * every other wiki answers it with a 404, so every lookup fell through to
 * English however the reader had set their language. The query API is older,
 * hands back the page as text rather than as senses, and is on every wiki there
 * is.
 *
 * Nothing here is configured and nothing is charged. It also never sees the
 * book: a dictionary cannot use a paragraph, and this is offered for single
 * words only.
 */
class WiktionaryTranslator(
    private val client: HttpClient,
    private val settings: TranslationSettingsRepository,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : Translator {

    override suspend fun translate(
        request: TranslationRequest,
        onPartial: (String) -> Unit
    ): Translation {
        val word = request.text.trim()
        val reader = request.targetLanguage.tag.substringBefore('-')

        val senses = try {
            // Short-circuited rather than looked up and discarded: a reader whose
            // own language is the fallback has one wiki to ask, and asking it
            // twice to throw the second answer away is a request nobody wanted.
            lookUp(word, reader)
                ?: if (reader == FALLBACK_WIKI) null else lookUp(word, FALLBACK_WIKI)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            throw failure.asTranslationFailure(settings.settings.value)
        }

        if (senses.isNullOrEmpty()) {
            throw AppErrorException(AppError.Unexpected("No dictionary entry for “$word”."))
        }

        return Translation(
            text = senses.first(),
            // The rest go where a translator's remark goes, under the paragraph:
            // a dictionary's second meaning is an aside about the word, not a
            // second thing to read in place of it.
            note = senses.drop(1).takeIf { it.isNotEmpty() }?.joinToString("  •  ")
        )
    }

    /**
     * The entry for one word on one wiki, or null when that wiki has no page for
     * it - which is an answer rather than a failure, and is why there is a second
     * wiki to try.
     */
    private suspend fun lookUp(word: String, wiki: String): List<String>? {
        val response = client.get("https://$wiki.wiktionary.org/w/api.php") {
            translationTimeouts(settings.settings.value)

            parameter("action", "query")
            parameter("prop", "extracts")
            parameter("titles", word)
            // The page as text rather than as markup, with its headings still
            // marked as headings - which is the only thing separating what a
            // word means from what part of speech it is.
            parameter("explaintext", "1")
            parameter("exsectionformat", "wiki")
            // A word written another way is the same word.
            parameter("redirects", "1")
            parameter("format", "json")
            parameter("formatversion", "2")
        }

        if (!response.status.isSuccess()) return null

        return response.entry()?.senses()
    }

    /** The page's own text, or null where the wiki has no page under that title. */
    private suspend fun HttpResponse.entry(): String? = runCatching {
        val page = json.parseToJsonElement(bodyAsText())
            .jsonObject["query"]
            ?.jsonObject
            ?.get("pages")
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?: return@runCatching null

        // A wiki says "there is no such page" rather than failing to answer.
        if (page.isMissing()) return@runCatching null

        page["extract"]?.jsonPrimitive?.content
    }.getOrNull()
}

private fun JsonObject.isMissing(): Boolean =
    this["missing"]?.jsonPrimitive?.content?.equals("true", ignoreCase = true) == true

/**
 * The senses in a page of a Wiktionary, out of the text of the whole page.
 *
 * A page is every language that spells the word this way, each with its parts of
 * speech and its senses under them, and the query API hands all of it over as one
 * run of text. What separates the senses from the scaffolding is that the
 * scaffolding is headings, which is why they are asked for with their markup
 * still on - a heading is a line inside `=` signs and a sense is a line that is
 * not.
 *
 * Deliberately not an attempt to find the right language's section. Which
 * language the word belongs to is what the reader is looking it up to find out,
 * the headings naming it are written in the wiki's language rather than in a code
 * this could match, and a wrong guess would throw away the only entry the page
 * had. The first few lines of an entry are its first few senses whichever section
 * they came from.
 */
private fun String.senses(): List<String> = lineSequence()
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .filterNot { it.startsWith("=") }
    // A cross-reference on its own line is a signpost to another entry rather
    // than a meaning, and reads as gibberish spliced into a sentence.
    .filterNot { it.startsWith("(") && it.endsWith(")") }
    .distinct()
    .take(MAX_SENSES)
    .toList()
