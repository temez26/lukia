package io.lukia.features.translate.domain.provider

/**
 * What the configured server says it can run.
 *
 * A port of its own rather than a method on [Translator], because the two are
 * asked at different times by different people: a translation happens while
 * somebody is reading, and this is asked once while they are setting things up.
 * A provider that cannot answer it - or will not - is still a perfectly good
 * translator.
 */
fun interface ModelCatalogue {

    /** The models this server offers, in the order they should be shown. */
    suspend fun models(): List<String>
}
