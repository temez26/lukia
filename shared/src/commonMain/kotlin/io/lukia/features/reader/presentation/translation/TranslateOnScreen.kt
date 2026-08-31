package io.lukia.features.reader.presentation.translation

import androidx.compose.runtime.Immutable

/**
 * The standing instruction about translating what the reader can see.
 *
 * Two fields rather than a callback, because what has to travel down to every
 * block of text is not an event but a state: a block scrolling into view has to
 * be able to ask "am I meant to be translating?" without anybody having tracked
 * where it was when the question was last asked.
 *
 * [generation] is bumped each time the reader asks for everything on screen
 * once. A block acts on a number it has not acted on yet and then remembers it,
 * which is what makes one request one round of translating rather than a
 * standing order - and what keeps a block scrolled into view afterwards out of
 * it, since it was not on screen when the reader asked.
 *
 * [continuous] is the standing order: text arriving in view is translated as it
 * arrives, until the reader turns it off.
 */
@Immutable
data class TranslateOnScreen(
    val generation: Long = 0,
    val continuous: Boolean = false,
) {

    /** The next round, for whoever is holding the button. */
    fun requested(): TranslateOnScreen = copy(generation = generation + 1)

    companion object {

        /** Nothing asked for, nothing standing. */
        val Idle = TranslateOnScreen()
    }
}

/**
 * How long text has to stay in view before a standing order asks about it.
 *
 * Only the standing order waits. A reader who presses the button is looking at
 * the page and wants it now; a reader scrolling with "keep translating" on is
 * throwing text past the window several screenfuls at a time, and without this
 * every block that flickered past would be asked about - a queue of paragraphs
 * the reader had already left behind, with whatever they finally stopped on
 * waiting at the end of it.
 *
 * Long enough to sit out a flick and short enough that stopping to read starts
 * the translating almost at once.
 */
internal const val TRANSLATE_SETTLE_MILLIS = 400L
