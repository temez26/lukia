package io.lukia.features.translate.infrastructure.wiktionary

/**
 * What a wiki said about one title, once it has been asked.
 *
 * The two cases are kept apart from "nobody has asked" - which is the null this
 * is returned as - because a wiki having no page for a word is an answer and not
 * a failure. It is the answer that costs the most to forget: a word missing from
 * the reader's own wiki is looked up on the English one as well, so re-asking a
 * miss is two requests rather than one.
 */
internal sealed interface CachedPage {

    data class Text(val extract: String) : CachedPage

    data object NoSuchPage : CachedPage
}

/**
 * Pages already read off a wiki, kept so that one word is fetched once however
 * often it is asked about.
 *
 * A port rather than a class for the same reason [TranslationCache] is one: what
 * is behind it is a storage decision. Nothing here is the reader's own writing,
 * only somebody else's encyclopaedia cached, so a platform that would rather
 * keep none of it can answer null throughout and lose nothing but requests.
 *
 * Reading is a write here: a page read back is marked as used, which decides who
 * survives the next trim.
 */
internal interface WiktionaryPageCache {

    suspend fun get(wiki: String, title: String): CachedPage?

    /** [extract] is null where the wiki has no page under that title. */
    suspend fun put(wiki: String, title: String, extract: String?)
}
