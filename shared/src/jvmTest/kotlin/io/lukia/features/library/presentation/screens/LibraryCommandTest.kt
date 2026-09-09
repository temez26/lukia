package io.lukia.features.library.presentation.screens

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import io.lukia.core.infrastructure.AppCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the library does with something the window was asked for - an "Add
 * Books…" off the desktop menu bar, or the shortcut standing in for it.
 *
 * The library carries these out because every one of them already has a control
 * on this screen. It does not *collect* them, and that is what these are really
 * about: this screen comes and goes as books are opened, while the menu bar is
 * live the whole time, so the collecting belongs to `DesktopShell` and only an
 * already-taken command reaches here.
 *
 * Nothing below starts Koin. That is a check rather than an omission - a
 * collector put back on this screen would need `AppCommands` out of the graph,
 * and every one of these would fail on the spot instead of the app quietly going
 * back to banking commands until the shelf next appeared.
 */
@OptIn(ExperimentalTestApi::class)
class LibraryCommandTest {

    private val carriedOut = mutableListOf<AppCommand>()
    private var handled = false

    /** [LibraryEffects] with the command it was handed, and nothing else. */
    @Composable
    private fun Effects(command: AppCommand?) {
        LibraryEffects(
            message = null,
            snackbarHostState = remember { SnackbarHostState() },
            onRefresh = {},
            onClearMessage = {},
            command = command,
            onCommand = {
                carriedOut += it
                handled = true
            },
        )
    }

    @Test
    fun aCommandHandedToTheLibraryIsCarriedOut() = runComposeUiTest {
        setContent { Effects(AppCommand.OpenSettings) }
        waitForIdle()

        assertEquals(listOf(AppCommand.OpenSettings), carriedOut)
        assertTrue(handled, "the shell was never told the command had been dealt with")
    }

    @Test
    fun nothingIsCarriedOutWhenNothingWasAsked() = runComposeUiTest {
        setContent { Effects(null) }
        waitForIdle()

        assertEquals(emptyList(), carriedOut)
    }

    /**
     * The round trip the shell and this screen make between them: the shell
     * holds a command until the screen says it has been dealt with, clears it,
     * and the same command asked for again has to arrive a second time.
     *
     * Without the clearing half, a reader could add books once per session and
     * the menu item would go dead.
     */
    @Test
    fun askingAgainAfterTheFirstWasClearedCarriesItOutAgain() = runComposeUiTest {
        var command by mutableStateOf<AppCommand?>(AppCommand.AddBooks)

        setContent { Effects(command) }
        waitForIdle()

        command = null
        waitForIdle()

        command = AppCommand.AddBooks
        waitForIdle()

        assertEquals(listOf(AppCommand.AddBooks, AppCommand.AddBooks), carriedOut)
    }
}
