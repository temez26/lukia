package io.lukia.features.translate.domain

import io.lukia.core.domain.BookMetadata

/**
 * What a book is, told to whatever is doing the translating.
 *
 * The point of this type is that a sentence out of a book is not a sentence out
 * of nowhere. "He drew his piece" is a weapon in a thriller and a chess move in
 * a memoir of tournaments, and no amount of looking at those four words settles
 * which. The title, the genre and the paragraph around the sentence are what
 * settle it - so they travel with every request, and a provider that can use
 * them does.
 *
 * A provider that cannot - a word-for-word dictionary API - simply ignores this,
 * which is why it is part of the request rather than part of the prompt.
 */
data class TranslationContext(
    val bookTitle: String? = null,
    val authors: List<String> = emptyList(),
    /** Genres, keywords, whatever the document filed itself under. */
    val genres: List<String> = emptyList(),
    /** A BCP 47 tag as the document wrote it, when it declared one. */
    val sourceLanguage: String? = null,
    val chapterTitle: String? = null,
    /**
     * The text the sentence sits in - normally its paragraph or its page.
     * Carried separately from the sentence itself so that whoever builds the
     * prompt can say which of the two is to be translated.
     */
    val surroundingText: String? = null,
) {

    /** True when the book told us nothing worth sending. */
    val isEmpty: Boolean
        get() = bookTitle.isNullOrBlank() &&
            authors.isEmpty() &&
            genres.isEmpty() &&
            sourceLanguage.isNullOrBlank() &&
            chapterTitle.isNullOrBlank() &&
            surroundingText.isNullOrBlank()

    companion object {
        val None = TranslationContext()
    }
}

/**
 * The context a book gives a passage, from what the document said about itself.
 *
 * [bookTitle] is passed in rather than read from the metadata alone because the
 * reader already knows what it is calling this book on screen, and a document
 * that declares no title of its own still has a name worth sending.
 *
 * Both lists are trimmed: an EPUB can file itself under thirty BISAC headings,
 * and the thirtieth says less about the book than the first three do.
 */
fun BookMetadata?.asTranslationContext(
    bookTitle: String? = null,
    chapterTitle: String? = null,
    surroundingText: String? = null,
): TranslationContext = TranslationContext(
    bookTitle = this?.title?.takeIf { it.isNotBlank() } ?: bookTitle?.takeIf { it.isNotBlank() },
    authors = this?.authors.orEmpty().filter { it.isNotBlank() }.take(MAX_AUTHORS),
    genres = this?.subjects.orEmpty().filter { it.isNotBlank() }.take(MAX_GENRES),
    sourceLanguage = this?.language?.takeIf { it.isNotBlank() },
    chapterTitle = chapterTitle?.takeIf { it.isNotBlank() },
    surroundingText = surroundingText?.takeIf { it.isNotBlank() },
)

private const val MAX_AUTHORS = 3
private const val MAX_GENRES = 6

/** A passage as the reader will see it, once something has translated it. */
data class Translation(
    val text: String,
    /**
     * A short remark from the translator, shown beside the sentence when there
     * is one - which word was ambiguous, what an idiom is doing. Usually null:
     * most sentences translate without anything worth saying about them, and a
     * note on every one of them would be noise rather than help.
     */
    val note: String? = null,
)

/**
 * Whatever can turn a passage into another language.
 *
 * The whole feature is written against this one function. What is behind it -
 * a model running on the device, an API with a key, a dictionary lookup - is a
 * choice made once in the dependency graph, and nothing above this line knows
 * which was made.
 *
 * Throws rather than returning a [Result]: the failures here are network and
 * configuration failures, which the use case narrows in one place along with
 * everything else that can go wrong.
 */
fun interface Translator {

    /**
     * [onPartial] is handed the translation as far as it has arrived, as often
     * as more of it does, and is never called with anything the finished
     * [Translation] will contradict - only with less of it.
     *
     * It exists because a passage is not worth waiting for in silence. A model
     * on the reader's own machine writes a sentence over several seconds, and
     * showing those seconds as a blank space is the difference between a slow
     * translation and one that looks broken. Whatever has nothing to report as
     * it goes - a cache, a dictionary lookup - simply never calls it.
     */
    suspend fun translate(
        request: TranslationRequest,
        onPartial: (String) -> Unit,
    ): Translation
}

/**
 * Translating for somebody who is not watching it arrive.
 *
 * The progress callback has no default of its own because a functional
 * interface cannot give one - and it should not become optional to *implement*
 * in any case. This is the other half of that: optional to *pass*, for the
 * callers that have nowhere to put a half-written sentence.
 */
suspend fun Translator.translate(request: TranslationRequest): Translation =
    translate(request) {}

/**
 * The longest passage worth sending.
 *
 * A sentence is a line or two and a paragraph a handful of them; this is well
 * past both. What it actually catches is a document with no sentence
 * punctuation in it at all - a badly converted text file where a whole chapter
 * is one "sentence" - which would otherwise be sent whole on a single tap and
 * come back as something nobody wanted to read.
 *
 * In the domain rather than inside the use case that enforces it, because it is
 * a fact about what can be translated rather than a rule one caller applies:
 * whoever gathers a page's worth of passages needs the same number to know what
 * it can ask about in one go.
 */
const val MAX_TRANSLATABLE_CHARS = 2000

/** One passage, the language it is wanted in, and the book it came out of. */
data class TranslationRequest(
    val text: String,
    val targetLanguage: TargetLanguage,
    val context: TranslationContext = TranslationContext.None,
    /**
     * How much of the book this is, which decides who is asked about it: a
     * single word can go to a dictionary, and everything larger cannot. See
     * [WordLookup].
     */
    val unit: PassageUnit = PassageUnit.Sentence,
)
