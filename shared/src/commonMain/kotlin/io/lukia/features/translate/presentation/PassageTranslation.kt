package io.lukia.features.translate.presentation

import io.lukia.core.domain.AppError
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

    data object Loading : PassageTranslation

    data class Ready(
        val translation: Translation,
        val showingOriginal: Boolean = false
    ) : PassageTranslation

    data class Failed(val error: AppError) : PassageTranslation
}

/**
 * The translation to draw in place of the original, or null when the original
 * is what should be on screen - because it is still being translated, because
 * translating it failed, or because the reader asked for it back.
 */
val PassageTranslation?.shownText: String?
    get() = (this as? PassageTranslation.Ready)
        ?.takeIf { !it.showingOriginal }
        ?.translation
        ?.text

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
    val surroundingText: String? = null
)
