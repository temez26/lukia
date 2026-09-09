package io.lukia.features.reader.presentation.translation

import androidx.compose.runtime.Immutable

/**
 * The standing instruction about translating what the reader can see.
 *
 * Two fields rather than a callback, because what travels down to every block of
 * text is a state, not an event: a block scrolling into view must be able to ask
 * "am I meant to be translating?" without anybody having tracked where it was
 * when the question was last asked.
 *
 * [generation] is bumped each time the reader asks for everything on screen
 * once. A block acts on a number it has not acted on and then remembers it,
 * which makes one request one round rather than a standing order - and keeps out
 * a block scrolled into view afterwards, which was not on screen when they
 * asked.
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
 * Only the standing order waits. Somebody who presses the button wants the page
 * now; somebody scrolling with "keep translating" on throws text past the window
 * screenfuls at a time, and without this every block that flickered past is
 * asked about - a queue of paragraphs already left behind, with whatever they
 * stopped on waiting at the end of it.
 *
 * Long enough to sit out a flick, short enough that stopping to read starts the
 * translating almost at once.
 */
internal const val TRANSLATE_SETTLE_MILLIS = 400L
