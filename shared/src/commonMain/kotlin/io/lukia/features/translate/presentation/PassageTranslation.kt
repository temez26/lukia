package io.lukia.features.translate.presentation

import io.lukia.core.domain.AppError
import io.lukia.features.translate.domain.PassageUnit
import io.lukia.features.translate.domain.TextAnchor
import io.lukia.features.translate.domain.Translation

/**
 * What has become of one passage the reader asked about.
 *
 * [Ready] carries which of the two the reader is currently looking at rather
 * than dropping the translation when they ask for the original back: going back
 * and forth is the point of the feature, and a reader who flipped to the
 * original and wanted the translation again should not have to wait for it
 * twice.
 */
sealed interface PassageTranslation {

    /**
     * [partial] is the translation as far as it has been written, for a
     * provider that streams - which every one of them now does. It is what
     * makes a slow model look like a sentence being written rather than like a
     * page that has stopped responding, and it is null only for the moment
     * between the tap and the first words coming back.
     */
    data class Loading(val partial: String? = null) : PassageTranslation

    data class Ready(
        val translation: Translation,
        val showingOriginal: Boolean = false,
    ) : PassageTranslation

    data class Failed(val error: AppError) : PassageTranslation
}

/**
 * The translation to draw in place of the original, or null when the original
 * is what should be on screen - because it is still being translated, because
 * translating it failed, or because the reader asked for it back.
 */
val PassageTranslation?.shownText: String?
    get() = when (this) {
        // Half a sentence in place of the original is the point: the reader
        // watches it being replaced, rather than watching nothing.
        is PassageTranslation.Loading -> partial

        is PassageTranslation.Ready -> translation.text.takeIf { !showingOriginal }

        else -> null
    }

/**
 * Whether something stands in place of the book's own words here - a
 * translation, as much of one as has arrived, or the reason one failed.
 *
 * Wider than [shownText] by exactly the failure, which carries no translation
 * and still has something to say. On a page that is a picture that something is
 * drawn *over* the words rather than beside them, and this is the question that
 * page has to ask: a passage covered up has none of its own words left on screen
 * for the reader to point at, so pointing anywhere inside it can only mean the
 * passage itself.
 */
val PassageTranslation?.replacesTheOriginal: Boolean
    get() = shownText != null || this is PassageTranslation.Failed

/**
 * Whether this passage should be marked on the page at all. A passage nobody
 * has touched is left exactly as the book set it.
 */
val PassageTranslation?.isMarked: Boolean
    get() = this != null

/**
 * A sentence the reader has just pointed at, with everything the book can say
 * about where it came from.
 *
 * One value rather than four arguments because it crosses three layers on its
 * way from the tap to the request, and because what a translator needs to know
 * about a passage is a thing worth naming.
 */
data class PassageSelection(
    val anchor: TextAnchor,
    val passage: String,
    val chapterTitle: String? = null,
    /** The paragraph or page the sentence sits in. */
    val surroundingText: String? = null,
    /**
     * How much of the book this is, which is what decides who is asked about it:
     * a single word can go to a dictionary and everything larger cannot. See
     * [io.lukia.features.translate.domain.WordLookup].
     */
    val unit: PassageUnit = PassageUnit.Sentence,
)
