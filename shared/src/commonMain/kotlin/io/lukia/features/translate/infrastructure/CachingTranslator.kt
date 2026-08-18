package io.lukia.features.translate.infrastructure

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
 * it, closing a book and opening it again throws away every translation in it,
 * and the second read of a page is charged exactly like the first - which is the
 * one thing a reader working through a book in a language they are learning will
 * do constantly.
 *
 * A decorator rather than a step inside the use case because caching is not
 * something translating a passage needs to know about: the port is
 * [Translator], this is a [Translator], and the composition happens once in the
 * dependency graph.
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
     * Who would be asked, as "provider/model", read per request for the same
     * reason [ConfiguredTranslator] resolves per request: it is a setting, and
     * the reader can change it with the book open.
     */
    private val translatorIdentity: () -> String
) : Translator {

    override suspend fun translate(request: TranslationRequest): Translation {
        val key = translationCacheKey(request, translatorIdentity())

        cache.get(key)?.let { return it }

        return translator.translate(request).also { cache.put(key, it) }
    }
}
