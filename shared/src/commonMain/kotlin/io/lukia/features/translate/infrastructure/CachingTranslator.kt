package io.lukia.features.translate.infrastructure

import io.lukia.features.translate.domain.CacheIdentity
import io.lukia.features.translate.domain.Translation
import io.lukia.features.translate.domain.TranslationCache
import io.lukia.features.translate.domain.TranslationRequest
import io.lukia.features.translate.domain.Translator
import io.lukia.features.translate.domain.translationCacheKey

/**
 * A translator that asks what is behind it only for passages nobody has asked
 * about before.
 *
 * This is where the feature stops costing money to re-read a chapter. Without
 * it, reopening a book throws away every translation in it and the second read
 * of a page is charged like the first - which is what a reader working through a
 * book in a language they are learning does constantly.
 *
 * A decorator rather than a step inside the use case, because caching is not
 * something translating a passage needs to know about: the port is [Translator],
 * this is a [Translator], and the composition happens once in the graph.
 *
 * A failure is not cached. What fails here is a key, a network or a service
 * having a bad minute, all of which are worth trying again - and a remembered
 * failure would be a sentence that could never be translated again without
 * clearing storage.
 */
class CachingTranslator(
    private val translator: Translator,
    private val cache: TranslationCache,
    /**
     * Who would be asked about this particular passage, and whether they read
     * the book around it - read per request for the same reason
     * [ConfiguredTranslator] resolves per request: it is a setting, and the
     * reader can change it with the book open.
     *
     * Per request rather than one answer for the app, because a word and a
     * sentence do not go to the same place - see [WordLookupTranslator] - and a
     * cache filing both under the translator's name hands the model's answer to
     * somebody who has since switched words to a dictionary.
     */
    private val answering: (TranslationRequest) -> CacheIdentity,
) : Translator {

    override suspend fun translate(
        request: TranslationRequest,
        onPartial: (String) -> Unit,
    ): Translation {
        val key = translationCacheKey(request, answering(request))

        // A remembered passage is not reported as arriving. There is nothing to
        // watch: it is already whole, and animating it in a piece at a time
        // would make the cheap case look like the expensive one.
        cache.get(key)?.let { return it }

        return translator.translate(request, onPartial).also { cache.put(key, it) }
    }
}
