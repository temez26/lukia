package io.lukia.features.translate.infrastructure.wiktionary

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.lukia.core.domain.AppError
import io.lukia.core.domain.AppErrorException
import io.lukia.features.translate.domain.TargetLanguage
import io.lukia.features.translate.domain.Translation
import io.lukia.features.translate.domain.TranslationRequest
import io.lukia.features.translate.domain.Translator
import io.lukia.features.translate.infrastructure.TranslationSettingsRepository
import io.lukia.features.translate.infrastructure.asTranslationFailure
import io.lukia.features.translate.infrastructure.translationTimeouts
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * How many senses of a word are worth putting in front of somebody who is
 * reading.
 *
 * A dictionary's whole advantage over a translator is that it does not choose,
 * and its whole risk is that it does not choose: a common verb has a dozen
 * entries, and a dozen entries spliced into a sentence is not something anybody
 * can read. The first few are the ones that answer most of the time.
 */
private const val MAX_SENSES = 3

/**
 * Looks a word up in Wiktionary.
 *
 * A dictionary rather than a translator, which is the point of offering it: it
 * gives the senses a word has instead of choosing one, which is what somebody
 * learning a language often actually wants from a word they do not know.
 *
 * Asked of the reader's own language's Wiktionary first, so the senses come back
 * in the language they are reading into, and of the English one when that has no
 * entry - which is often, because the smaller Wiktionaries are much thinner. The
 * answer is then in English rather than in their language, which is worse than
 * the right language and far better than nothing.
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

        val entry = try {
            lookUp(word, request.targetLanguage.tag) ?: lookUp(word, FALLBACK_WIKI)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            throw failure.asTranslationFailure(settings.settings.value)
        }

        if (entry == null) {
            throw AppErrorException(
                AppError.Unexpected("No dictionary entry for “$word”.")
            )
        }

        return entry
    }

    private suspend fun lookUp(word: String, wiki: String): Translation? {
        // The tag can carry a script - zh-Hans - and there is no wiki under that
        // name. The language part is the one that names a Wiktionary.
        val host = "${wiki.substringBefore('-')}.wiktionary.org"

        val response = client.get("https://$host/api/rest_v1/page/definition/${word.encoded()}") {
            translationTimeouts(settings.settings.value)
        }

        // A word with no entry is a 404, which is an answer rather than a
        // failure - it is why there is a second wiki to try.
        if (!response.status.isSuccess()) return null

        return response.senses()?.let { senses ->
            Translation(
                text = senses.first(),
                // The rest of the senses go where a translator's remark goes,
                // under the paragraph: a dictionary's second meaning is an aside
                // about the word, not a second thing to read in place of it.
                note = senses.drop(1).takeIf { it.isNotEmpty() }?.joinToString("  •  ")
            )
        }
    }

    /**
     * The senses in the entry, flattened out of the parts of speech they are
     * grouped under.
     *
     * Read by hand rather than into a type, because what comes back is an object
     * whose keys are language codes - one per language that spells the word this
     * way - and a shape whose field names are data is a shape no serializer will
     * describe. Every language in it is taken: the reader is reading one of them
     * and the entry does not say which.
     */
    private suspend fun HttpResponse.senses(): List<String>? {
        val body = runCatching {
            json.parseToJsonElement(bodyAsText()).jsonObject
        }.getOrNull() ?: return null

        val senses = body.values
            .filterIsInstance<JsonArray>()
            .flatMap { byPartOfSpeech -> byPartOfSpeech.mapNotNull { it as? JsonObject } }
            .flatMap { part ->
                (part["definitions"] as? JsonArray).orEmpty().mapNotNull { definition ->
                    (definition as? JsonObject)
                        ?.get("definition")
                        ?.jsonPrimitive
                        ?.content
                        ?.withoutMarkup()
                        ?.takeIf { it.isNotBlank() }
                }
            }
            .distinct()
            .take(MAX_SENSES)

        return senses.takeIf { it.isNotEmpty() }
    }
}

private const val FALLBACK_WIKI = "en"

private fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> = this ?: emptyList()

/**
 * A definition with its links and its emphasis taken out.
 *
 * Wiktionary hands these back as fragments of HTML - a definition is full of
 * links to the words in it - and a reader who tapped a word wants the sense, not
 * the markup around it. Stripped rather than rendered: this goes into a
 * paragraph of a book, set in the book's own face, where a link would be a
 * thing to tap that leads nowhere.
 */
private fun String.withoutMarkup(): String {
    val text = StringBuilder(length)
    var insideTag = false

    forEach { character ->
        when {
            character == '<' -> insideTag = true
            character == '>' -> insideTag = false
            !insideTag -> text.append(character)
        }
    }

    return text.toString().unescapedEntities().replace(WHITESPACE, " ").trim()
}

private val WHITESPACE = Regex("\\s+")

/** The handful of entities Wiktionary's definitions actually contain. */
private fun String.unescapedEntities(): String = this
    .replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace("&nbsp;", " ")

/**
 * The word as it can be put in a path.
 *
 * Only what has to be: a Wiktionary title is words, and the characters that
 * would break a path out of one are few. Everything above the ASCII range is
 * left alone, because the words being looked up here are exactly the accented
 * and non-Latin ones and percent-encoding them by hand is how that goes wrong.
 */
private fun String.encoded(): String = buildString {
    this@encoded.forEach { character ->
        when (character) {
            ' ' -> append("_")
            '/' -> append("%2F")
            '?' -> append("%3F")
            '#' -> append("%23")
            '&' -> append("%26")
            '%' -> append("%25")
            else -> append(character)
        }
    }
}
