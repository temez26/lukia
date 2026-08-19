package io.lukia.features.reader.presentation

import androidx.compose.runtime.Immutable

/**
 * What the reader's own translations are currently doing, as much of it as the
 * control that shows and hides them needs to know.
 *
 * Two fields rather than one, because "there is nothing to show" and "what there
 * is, is hidden" are different states and the control has to look different in
 * each: nothing to show means there is nothing to press.
 */
@Immutable
data class ShownTranslations(
    /** Whether the book has any finished translations at all. */
    val any: Boolean = false,
    /** Whether the ones it has are the words currently on the page. */
    val visible: Boolean = false
) {

    companion object {
        val None = ShownTranslations()
    }
}
