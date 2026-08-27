package io.lukia.features.library.presentation.screens

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import io.lukia.core.infrastructure.AppCommand
import io.lukia.core.infrastructure.AppCommands
import org.koin.compose.koinInject

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
    message: String?,
    snackbarHostState: SnackbarHostState,
    onRefresh: () -> Unit,
    onClearMessage: () -> Unit,
    onCommand: (AppCommand) -> Unit,
) {
    // Read through the state rather than captured, so a caller passing a fresh
    // lambda each composition does not tear these down and start them again -
    // which for the first of them would be a re-read of the whole library.
    val refresh by rememberUpdatedState(onRefresh)
    val clear by rememberUpdatedState(onClearMessage)
    val command by rememberUpdatedState(onCommand)

    // Fires again each time the grid is re-entered from the detail screen, which
    // may have favourited or removed a book behind its back.
    LaunchedEffect(Unit) {
        refresh()
    }

    // Cleared once it has been seen, so the same notice is not shown again the
    // next time anything about the library changes.
    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            clear()
        }
    }

    // What a desktop menu bar asked for - see [AppCommands]. Collected here
    // rather than in the screen because it is one more standing arrangement and
    // draws nothing, which is what this function is for. Nothing arrives on the
    // platforms with no menu bar, so the collector simply waits there.
    val commands: AppCommands = koinInject()

    LaunchedEffect(commands) {
        commands.commands.collect { requested -> command(requested) }
    }
}
