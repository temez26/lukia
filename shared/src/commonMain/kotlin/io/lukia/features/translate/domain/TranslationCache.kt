package io.lukia.features.translate.domain

import kotlin.jvm.JvmInline

/**
 * Passages already translated, kept so that the same words are never sent twice.
 *
 * A port rather than a class because what is behind it is a storage decision:
 * today a table, tomorrow nothing at all on a platform that would rather not
 * keep the reader's book on disk.
 *
 * Reading is a write here - [get] marks the entry as used, which is what decides
 * who survives the next trim.
 */
interface TranslationCache {

    suspend fun get(key: TranslationCacheKey): Translation?

    suspend fun put(key: TranslationCacheKey, translation: Translation)
}

/**
 * What a cached translation is filed under.
 *
 * A value rather than a bare [String] so it cannot be confused with anything
 * else here that is also a string - the passage, the model's name - which is
 * most of what goes wrong with a cache.
 */
@JvmInline
value class TranslationCacheKey(val value: String)

/**
 * Who would be asked about a passage, and whether the book it came out of
 * changes what they would say.
 *
 * The second half is what lets one word be paid for once. A model is handed the
 * paragraph and the title and uses both - "he drew his piece" is a weapon or a
 * chess move depending on them - so its answer belongs to that passage in that
 * book. A dictionary and Google are handed the word and the language and nothing
 * else: their answer for `values` is the same in every book on the shelf, and
 * keyed by the book it would be fetched again for each.
 *
 * Read per request rather than settled once, because which of the two answers a
 * word is a setting the reader can change with the book open - see
 * [io.lukia.features.reader.domain.text.WordLookup].
 */
data class CacheIdentity(
    /** Who would be asked, as "provider/model" - see [TranslationProvider]. */
    val translator: String,
    val readsTheBook: Boolean,
    /**
     * Which wording of the instructions the answer was given to, for whoever is
     * given instructions at all - see
     * [io.lukia.features.translate.infrastructure.TRANSLATION_PROMPT_VERSION].
     *
     * Null for a dictionary and for Google, which are handed a word and no
     * instructions at all: their answers do not change when this app changes its
     * mind about how to ask, and filing them under a version would throw away a
     * good lookup at every update.
     */
    val promptVersion: String? = null,
)

/**
 * The key for one request, which is a hash of everything that decides the
 * answer - and of nothing that does not.
 *
 * The passage, the language wanted, who would be asked, and - for whoever is
 * asked in words - which wording they were asked in. Leave any out and the cache
 * answers a question nobody asked: readers do switch between two models to
 * compare them, and a cache ignoring the model would hand them the same sentence
 * twice. The wording is there for the same reason one update further out: an
 * answer to instructions this build has since rewritten answers a question it no
 * longer asks.
 *
 * The book and the paragraph only for whoever reads them, which is the whole of
 * how a word looked up in one book is already answered in the next - see
 * [CacheIdentity]. Including them regardless filed a dictionary's answer under
 * the paragraph it happened to be read in, so the same word four pages later was
 * a fresh lookup with an identical answer.
 *
 * Hashed rather than stored whole because the surrounding text is up to a page
 * of the book: keyed by the text itself the cache would be a slow second copy of
 * the library. The two hashes give 128 bits together, at which a collision in a
 * table of a few thousand entries does not happen.
 */
fun translationCacheKey(
    request: TranslationRequest,
    identity: CacheIdentity,
): TranslationCacheKey {
    val fingerprint = buildString {
        append(identity.translator).append(SEPARATOR)
        append(identity.promptVersion.orEmpty()).append(SEPARATOR)
        append(request.targetLanguage.name).append(SEPARATOR)
        append(request.text.trim()).append(SEPARATOR)

        if (identity.readsTheBook) request.context.appendTo(this)
    }.encodeToByteArray()

    return TranslationCacheKey(
        fingerprint.fnv1a(FIRST_OFFSET, FIRST_PRIME, reversed = false).hex() +
            fingerprint.fnv1a(SECOND_OFFSET, SECOND_PRIME, reversed = true).hex(),
    )
}

/**
 * Everything the book said about the passage, in a fixed order.
 *
 * The separators matter: without them an author called "A" writing about "BC"
 * and one called "AB" writing about "C" run together into the same key.
 */
private fun TranslationContext.appendTo(builder: StringBuilder) {
    with(builder) {
        append(bookTitle.orEmpty()).append(SEPARATOR)
        append(authors.joinToString(SEPARATOR)).append(SEPARATOR)
        append(genres.joinToString(SEPARATOR)).append(SEPARATOR)
        append(sourceLanguage.orEmpty()).append(SEPARATOR)
        append(chapterTitle.orEmpty()).append(SEPARATOR)
        append(surroundingText.orEmpty())
    }
}

/**
 * FNV-1a written out rather than taken from the platform, because this key goes
 * to disk: built on `String.hashCode` it would be a cache that quietly emptied
 * itself the day a runtime changed its mind about hashing.
 *
 * The two halves differ by more than their starting value - a different prime,
 * and the bytes read the other way - because two runs of one function over the
 * same bytes are not two independent chances of telling two passages apart.
 */
private fun ByteArray.fnv1a(offset: Long, prime: Long, reversed: Boolean): Long {
    var hash = offset

    val order = if (reversed) indices.reversed() else indices

    order.forEach { index ->
        hash = hash xor (this[index].toLong() and 0xFF)
        hash *= prime
    }

    return hash
}

private fun Long.hex(): String = buildString(16) {
    for (shift in 60 downTo 0 step 4) {
        append(HEX_DIGITS[((this@hex ushr shift) and 0xF).toInt()])
    }
}

private const val HEX_DIGITS = "0123456789abcdef"

/**
 * A character no part of a book, a title or a model name contains, so that the
 * boundaries between the fields cannot be forged by their contents.
 */
private const val SEPARATOR = "\u0000"

private const val FIRST_OFFSET = -3750763034362895579L
private const val FIRST_PRIME = 1099511628211L

private const val SECOND_OFFSET = -1234567890123456789L
private const val SECOND_PRIME = 880355519L
