package io.lukia.features.reader.presentation

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/**
 * What a key press asks the reader to do.
 *
 * Named for the reader's own vocabulary rather than the keys, so the screen that
 * carries these out reads as a list of things a reader can do and the mapping
 * below stays the only place that knows which key does which.
 */
internal enum class ReaderCommand {
    NextPage,
    PreviousPage,
    FirstPage,
    LastPage,

    /** A nudge rather than a page, for reading down a page that is taller than the window. */
    ScrollDown,
    ScrollUp,

    Wider,
    Narrower,
    ActualSize,

    ToggleContents,
    ToggleSettings,

    /** Translate everything on screen, once. */
    TranslatePage,

    /** Keep translating what comes into view, until asked to stop. */
    ToggleContinuousTranslation,

    /** Put every translation away, or bring them all back. */
    ToggleTranslationsShown,

    Close,
}

/**
 * The command [event] asks for, or null if it asks for nothing.
 *
 * Every shortcut here has a control on screen that does the same thing, except
 * moving through the book - and that is scrolling, which every platform already
 * gives a keyboard. Nothing is only reachable by memorising a key.
 *
 * Bare keys rather than modifier combinations: there is nowhere in the reader to
 * type, so nothing to take the letters away, and a reading app whose page turn
 * needs two hands has missed the point. Anything a modifier is held down for is
 * left alone, so the platform's own shortcuts - a window closed, a screenshot
 * taken - still reach it.
 */
internal fun readerCommandFor(event: KeyEvent): ReaderCommand? {
    if (event.type != KeyEventType.KeyDown) return null
    if (event.isAltPressed || event.isCtrlPressed || event.isMetaPressed) return null

    return when (event.key) {
        Key.PageDown, Key.Spacebar, Key.DirectionRight -> ReaderCommand.NextPage
        Key.PageUp, Key.DirectionLeft -> ReaderCommand.PreviousPage
        Key.MoveHome -> ReaderCommand.FirstPage
        Key.MoveEnd -> ReaderCommand.LastPage

        Key.DirectionDown -> ReaderCommand.ScrollDown
        Key.DirectionUp -> ReaderCommand.ScrollUp

        // The key with the plus on it is the equals key unshifted, and both
        // arrive here, so both mean the same thing rather than one of them
        // meaning nothing on the keyboard the reader actually has.
        Key.Plus, Key.Equals, Key.NumPadAdd -> ReaderCommand.Wider
        Key.Minus, Key.NumPadSubtract -> ReaderCommand.Narrower
        Key.Zero, Key.NumPad0 -> ReaderCommand.ActualSize

        Key.C -> ReaderCommand.ToggleContents
        Key.T -> ReaderCommand.ToggleSettings

        // Beside each other under the same finger, because they are the same
        // thing asked once and asked continuously.
        Key.R -> ReaderCommand.TranslatePage
        Key.E -> ReaderCommand.ToggleContinuousTranslation
        Key.H -> ReaderCommand.ToggleTranslationsShown

        Key.Escape -> ReaderCommand.Close

        else -> null
    }
}

/** How much of the window a nudge moves, leaving a line or two of overlap to read on from. */
internal const val SCROLL_NUDGE_FRACTION = 0.4f

/** One step of the page width, matching what the settings panel's presets step by. */
internal const val PAGE_SCALE_STEP = 0.1f
