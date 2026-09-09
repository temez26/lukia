package io.lukia.features.library.presentation.screens

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import io.lukia.core.infrastructure.AppCommand
import io.lukia.features.library.presentation.model.LibraryMessage
import io.lukia.features.library.presentation.model.displayText

/**
 * What the library screen does besides draw itself.
 *
 * Kept out of the screen for the reason `ReaderReadingEffects` is kept out of
 * the reader: neither of these draws anything, and a screen that reads as a
 * layout is easier to be sure of than one with standing arrangements threaded
 * through it.
 */
@Composable
internal fun LibraryEffects(
    message: LibraryMessage?,
    snackbarHostState: SnackbarHostState,
    onRefresh: () -> Unit,
    onClearMessage: () -> Unit,
    /**
     * What the app was asked for from outside its own screens, waiting for the
     * screen that can carry it out. Null where nothing has been asked, which is
     * every platform without a menu bar and most of the time on the one with it.
     */
    command: AppCommand?,
    onCommand: (AppCommand) -> Unit,
) {
    // Read through the state rather than captured, so a caller passing a fresh
    // lambda each composition does not tear these down and start them again -
    // which for the first of them would be a re-read of the whole library.
    val refresh by rememberUpdatedState(onRefresh)
    val clear by rememberUpdatedState(onClearMessage)
    val carryOut by rememberUpdatedState(onCommand)

    // Fires again each time the grid is re-entered from the detail screen, which
    // may have favourited or removed a book behind its back.
    LaunchedEffect(Unit) {
        refresh()
    }

    // Put into words here rather than in the effect below: `LaunchedEffect` is
    // a coroutine rather than a composition, and a string resource needs the
    // composition it is read in.
    val notice = message?.displayText()

    // Cleared once it has been seen, so the same notice is not shown again the
    // next time anything about the library changes.
    LaunchedEffect(notice) {
        notice?.let {
            snackbarHostState.showSnackbar(it)
            clear()
        }
    }

    // What a desktop menu bar asked for - see [AppCommand]. Handed down already
    // taken rather than collected here, which is the one thing this must not do:
    // the menu bar belongs to the window and is live on every screen, while this
    // composable exists only while the library is on screen. `DesktopShell` does
    // the collecting, for as long as there is a window to collect for.
    //
    // The same command twice running is carried out once - the key does not
    // change - which is what a menu item held down should do.
    LaunchedEffect(command) {
        command?.let(carryOut)
    }
}
