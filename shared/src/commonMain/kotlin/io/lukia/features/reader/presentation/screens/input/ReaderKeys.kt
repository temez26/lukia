package io.lukia.features.reader.presentation.screens.input

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onKeyEvent
import io.lukia.features.reader.presentation.ReaderCommand
import io.lukia.features.reader.presentation.readerCommandFor
import io.lukia.features.reader.presentation.screens.ReaderChromeState

/**
 * The keyboard on the reader: holding the focus it needs, and turning a key
 * press into one of the book's own commands.
 *
 * A modifier rather than a piece of the screen because that is what it is - the
 * three things below all hang off one element, and threaded through the screen
 * they read as layout when none of them draws anything.
 *
 * The reader takes the keyboard as it opens, so the shortcuts work without a
 * click first, and takes it back whenever it loses it entirely. Losing it is not
 * hypothetical: pressing a toolbar button moves focus into the chrome, and
 * hiding the chrome then takes the focused control out of the window - which
 * leaves nothing focused, and so nothing for a key press to reach. That is the
 * reader who put the bar away and found their shortcuts had gone with it.
 *
 * Only when it is lost altogether. Focus on a control in the chrome is left
 * where it is, and the handler here runs after that control has had its say - so
 * Space on a focused segment chooses it rather than turning a page.
 */
@Composable
internal fun Modifier.readerKeys(
    chrome: ReaderChromeState,
    onCommand: (ReaderCommand) -> Unit,
): Modifier {
    val focusRequester = remember { FocusRequester() }

    // Whether the keyboard is anywhere in the reader at all - on the book
    // itself, or on a control in the chrome over it.
    var holdsFocus by remember { mutableStateOf(false) }

    LaunchedEffect(holdsFocus) {
        if (!holdsFocus) focusRequester.requestFocus()
    }

    return onFocusChanged { holdsFocus = it.hasFocus }
        .focusRequester(focusRequester)
        .focusable()
        .onKeyEvent { event ->
            val command = readerCommandFor(event) ?: return@onKeyEvent false

            // Escape closes whatever is open on top of the book before it closes
            // the book. Handled here rather than left to the panels' own back
            // handler: that one answers to the platform's back gesture, which
            // not every platform routes a key into, and "Escape leaves the book"
            // firing under an open panel is not a mistake worth risking.
            if (command == ReaderCommand.Close && chrome.panelOpen) {
                chrome.closePanels()
                return@onKeyEvent true
            }

            // A shortcut is a reason to show the chrome: it is how the reader
            // sees that the page width changed, or which page they landed on.
            chrome.showChrome()
            onCommand(command)
            true
        }
}
