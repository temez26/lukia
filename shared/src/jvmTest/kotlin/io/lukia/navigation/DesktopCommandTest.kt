package io.lukia.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.lukia.core.infrastructure.AppCommand
import io.lukia.core.infrastructure.AppCommands
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The desktop menu bar reaching the app from wherever the reader happens to be.
 *
 * The shape below is `DesktopShell`'s, stripped to what the question needs: the
 * collector sits above the host, and the destinations under it come and go. That
 * is the whole of it - the menu bar and the shortcuts standing in for it are
 * mounted on the Window and are live on every screen, so whatever takes what
 * they send has to be live there too.
 *
 * Collected inside the library destination, as it was, the first test fails. The
 * command is not lost - a channel buffers it - but nothing takes it while a book
 * is open, and it is then carried out at whatever later moment the reader
 * happens to navigate back, opening a file panel for a keypress from minutes
 * earlier.
 *
 * How the waiting is done is not incidental here - see [settle].
 *
 * No Koin: `DesktopShell` resolves `AppCommands` and hands it over, so what is
 * under test takes its collaborator as an argument and this can build one.
 */
@OptIn(ExperimentalTestApi::class)
class DesktopCommandTest {

    private val commands = AppCommands()

    private var taken: AppCommand? = null
    private var controller: NavHostController? = null

    /** `DesktopShell` in miniature: the collector above the host, screens below it. */
    @Composable
    private fun Shell() {
        val navController = rememberNavController()
        controller = navController

        // Read in the composition rather than off the returned state, which is
        // how the shell reads it and the only way a write from the collector's
        // own coroutine reaches this test.
        val requested by rememberRequestedCommand(commands, navController)
        taken = requested

        NavHost(navController = navController, startDestination = Shelf) {
            composable(Shelf) { Text(ShelfText) }
            composable(Book) { Text(BookText) }
        }
    }

    @Test
    fun aCommandAskedForWhileABookIsOpenIsTakenAndBringsTheShelfBack() = runComposeUiTest {
        setContent { Shell() }
        waitForIdle()

        onNodeWithText(ShelfText).assertExists()

        runOnUiThread { controller?.navigate(Book) }
        waitForIdle()

        // The library is out of the composition now, which is the whole point.
        onNodeWithText(BookText).assertExists()
        onNodeWithText(ShelfText).assertDoesNotExist()

        commands.request(AppCommand.AddBooks)
        settle { taken != null }

        assertEquals(AppCommand.AddBooks, taken, "nothing took the command off the menu bar")
        onNodeWithText(ShelfText).assertExists()
    }

    @Test
    fun nothingIsHeldUntilSomethingIsAskedFor() = runComposeUiTest {
        setContent { Shell() }
        waitForIdle()

        assertNull(taken)
    }

    /**
     * Runs frames, and lets real time pass between them, until [arrived] holds.
     *
     * `waitUntil` will not do, and neither will frames on their own. What this
     * waits for is a coroutine resuming on a channel receive, off this thread:
     * `waitUntil` advances a virtual clock, so a thousand simulated milliseconds
     * go by without the continuation ever running. This returns as soon as the
     * command lands - a frame or two in practice - with a budget many times that,
     * so a loaded machine has no way to make it flake.
     */
    private fun ComposeUiTest.settle(arrived: () -> Boolean) {
        repeat(Frames) {
            waitForIdle()
            if (arrived()) return
            Thread.sleep(BetweenFrames)
        }
        waitForIdle()
    }

    private companion object {
        const val Shelf = "library"
        const val Book = "reader"
        const val ShelfText = "the shelf"
        const val BookText = "the book"
        const val Frames = 20
        const val BetweenFrames = 50L
    }
}
