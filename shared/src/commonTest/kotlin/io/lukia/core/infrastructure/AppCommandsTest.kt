package io.lukia.core.infrastructure

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What a desktop menu bar asks the app for, and how it reaches the screen that
 * can carry it out.
 *
 * Pinned here is the hand-off itself. A command is taken once by one collector
 * and never handed out again - which is what stops a resubscribe opening a
 * second file panel - and one asked for before anything is collecting waits
 * rather than vanishing, because the menu bar exists from the moment the window
 * does and the shelf does not.
 *
 * The other half of getting this right cannot be asserted from here: the
 * collector has to outlive every screen, or a command asked for while a book is
 * open is banked rather than lost and then carried out at whatever later moment
 * the shelf happens to come back. That is why `DesktopShell` collects and
 * `LibraryEffects` does not - the latter takes a command already taken, and
 * composes with no dependency graph behind it at all.
 */
class AppCommandsTest {

    @Test
    fun aCommandAskedForBeforeAnythingCollectsIsNotLost() = runTest {
        val commands = AppCommands()

        // The window and its menu bar exist before the composition does.
        commands.request(AppCommand.AddBooks)

        assertEquals(AppCommand.AddBooks, commands.commands.first())
    }

    @Test
    fun commandsArriveInTheOrderTheyWereAskedFor() = runTest {
        val commands = AppCommands()

        commands.request(AppCommand.AddBooks)
        commands.request(AppCommand.AddFolder)
        commands.request(AppCommand.OpenSettings)

        assertEquals(
            listOf(AppCommand.AddBooks, AppCommand.AddFolder, AppCommand.OpenSettings),
            commands.commands.take(3).toList(),
        )
    }

    @Test
    fun aCommandTakenOnceIsNotHandedOutAgain() = runTest {
        val commands = AppCommands()

        commands.request(AppCommand.AddBooks)
        assertEquals(AppCommand.AddBooks, commands.commands.first())

        commands.request(AppCommand.OpenSettings)

        // The whole reason this is a channel and not a replaying shared flow:
        // whoever collects next must not be handed "Add Books" a second time and
        // open a file panel nobody asked for.
        assertEquals(AppCommand.OpenSettings, commands.commands.first())
    }

    @Test
    fun pastWhatItCanHoldTheOldestGoesRatherThanTheNewest() = runTest {
        val commands = AppCommands()
        val taken = mutableListOf<AppCommand>()

        // Nothing is collecting, so these queue - and then stop fitting.
        repeat(MoreThanItHolds) { commands.request(AppCommand.AddBooks) }
        commands.request(AppCommand.OpenSettings)

        backgroundScope.launch { commands.commands.toList(taken) }
        runCurrent()

        assertTrue(
            taken.size <= MoreThanItHolds,
            "nothing was dropped: ${taken.size} of ${MoreThanItHolds + 1} arrived",
        )

        // Somebody this far ahead of the app is holding down a shortcut, and the
        // last press is the one they mean.
        assertEquals(AppCommand.OpenSettings, taken.last())
    }

    private companion object {
        /** Comfortably past the channel's own capacity, which is its business. */
        const val MoreThanItHolds = 64
    }
}
