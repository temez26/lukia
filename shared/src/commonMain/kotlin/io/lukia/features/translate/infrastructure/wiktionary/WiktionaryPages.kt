package io.lukia.features.translate.infrastructure.wiktionary

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.lukia.features.translate.domain.lookup.WordEntry
import io.lukia.features.translate.domain.settings.TargetLanguage
import io.lukia.features.translate.infrastructure.TranslationSettingsRepository
import io.lukia.features.translate.infrastructure.translating
import io.lukia.features.translate.infrastructure.translationTimeouts
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** What is read when the reader's own language has no entry for the word. */
private const val FALLBACK_WIKI = "en"

/**
 * One word's page, off whichever Wiktionary is most likely to have it.
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
 * Its own type rather than part of the translator that first needed it, because
 * two things now ask this question of the same wikis: a word being translated in
 * the line it sits on, and the whole entry the reader asked to see. What differs
 * between them is how much of the page they keep - see [reading] - and that is
 * not a reason to fetch it two ways.
 *
 * Nothing here is configured and nothing is charged. It also never sees the
 * book: a dictionary cannot use a paragraph.
 */
internal class WiktionaryPages(
    private val client: HttpClient,
    private val settings: TranslationSettingsRepository,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    /**
     * What a wiki has to say about one word, with a page that only says which
     * word it is a form of followed to the word itself.
     *
     * That last part is most of what makes this useful on real prose. A book is
     * written in inflected words, and a wiki's page for one of them is often not
     * a definition: `values` answers `monikkomuoto sanasta value` and nothing
     * else, which tells a reader who wanted `arvo` only that they have more
     * looking up to do. See [baseForm].
     *
     * Followed once and no further. A chain of pointers is not a thing wikis
     * write, and a lookup that could walk one would be a lookup that could loop.
     *
     * What the word's own page said becomes [WordEntry.form] either way - the
     * grammar is worth keeping whether or not the base form answered, and it is
     * the one thing that explains why the reader is being shown a word they did
     * not hold. A base form with no entry of its own therefore leaves an entry
     * with a form and no senses, which is an answer rather than a failure: it
     * has somewhere to go, which is the panel, and nowhere to go on the page.
     *
     * [WordEntry.word] is the word actually defined, which is not always the one
     * that was asked about. Whoever shows it can say so.
     */
    suspend fun entryFor(
        word: String,
        targetLanguage: TargetLanguage,
        limit: Int,
    ): WordEntry? {
        val term = word.trim()
        val own = entry(term, targetLanguage)?.reading(term, limit) ?: return null
        val base = own.senses.baseForm(term) ?: return own.asEntry(term)

        val form = own.senses.first()
        val defined = entry(base, targetLanguage)
            ?.reading(base, limit)
            ?.takeIf { it.senses.isNotEmpty() }

        return if (defined == null) {
            own.withoutTheForm().asEntry(term, form)
        } else {
            defined.asEntry(base, form)
        }
    }

    /**
     * The page for [word], as one run of text, or null where no wiki has one.
     *
     * Asked under the word as the book spelled it and then under its lower case,
     * because a Wiktionary title is case sensitive in a way a Wikipedia title is
     * not: `juoksi` has a page and `Juoksi` has none, so a word that happened to
     * open a sentence was reported as a word no dictionary had ever heard of.
     * The word as written is asked first even so - `Rooma` and `rooma` are
     * different words, and the one the book capitalised is the one it meant.
     *
     * The reader's own wiki is exhausted before the English one is opened, since
     * a Finnish reader would rather have the Finnish page for a word than the
     * English page for the same word.
     */
    private suspend fun entry(word: String, targetLanguage: TargetLanguage): String? {
        val reader = targetLanguage.tag.substringBefore('-')

        // Short-circuited rather than looked up and discarded: a reader whose own
        // language is the fallback has one wiki to ask, and asking it twice to
        // throw the second answer away is a request nobody wanted.
        val wikis = listOf(reader, FALLBACK_WIKI).distinct()
        val titles = listOf(word, word.lowercase()).distinct()

        return translating(settings.settings.value) {
            wikis.firstNotNullOfOrNull { wiki ->
                titles.firstNotNullOfOrNull { title -> page(title, wiki) }
            }
        }
    }

    /**
     * The page for one word on one wiki, or null when that wiki has none for it -
     * which is an answer rather than a failure, and is why there is a second wiki
     * to try.
     */
    private suspend fun page(word: String, wiki: String): String? {
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

        return response.extract()
    }

    /** The page's own text, or null where the wiki has no page under that title. */
    private suspend fun HttpResponse.extract(): String? = runCatching {
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
 * The reading of one page as the entry the rest of the app sees, said once
 * rather than at each of the three places [WiktionaryPages.entryFor] can end.
 */
private fun WiktionaryReading.asEntry(word: String, form: String? = null): WordEntry = WordEntry(
    word = word,
    meanings = meanings,
    form = form,
    related = related,
)

/**
 * The reading with the line that was only a pointer taken out of it.
 *
 * Reached when the base form it pointed at has no page of its own, and what is
 * left has to be the entry without the grammar: the grammar has somewhere of its
 * own to go - see [WordEntry.form] - and printed in both places it reads as a
 * meaning of the word, which is the one thing it is not.
 */
private fun WiktionaryReading.withoutTheForm(): WiktionaryReading = copy(
    meanings = meanings
        .mapIndexed { index, meaning ->
            if (index == 0) meaning.copy(senses = meaning.senses.drop(1)) else meaning
        }
        .filter { it.senses.isNotEmpty() },
)
