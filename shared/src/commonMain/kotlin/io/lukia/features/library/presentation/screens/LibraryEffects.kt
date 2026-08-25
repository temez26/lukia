package io.lukia.features.library.presentation.screens

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

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
) {
    // Read through the state rather than captured, so a caller passing a fresh
    // lambda each composition does not tear these down and start them again -
    // which for the first of them would be a re-read of the whole library.
    val refresh by rememberUpdatedState(onRefresh)
    val clear by rememberUpdatedState(onClearMessage)

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
}
