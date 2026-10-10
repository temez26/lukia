package io.lukia.core.infrastructure

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Something the reader asked for from outside the app's own screens.
 *
 * Every one of these already has a control on a screen that does the same
 * thing - this is a second way to reach it, not a second implementation of it.
 * A desktop menu bar is where they come from today; nothing else sends any.
 */
enum class AppCommand {
    AddBooks,
    AddFolder,
    OpenSettings,
}

/**
 * The hand-off between a desktop menu and the screen that can carry it out.
 *
 * The same shape as [DocumentInbox] and for the same reason: a menu bar belongs
 * to the window, created before there is a composition, while what imports a
 * book or opens the settings is a screen deep inside one.
 *
 * A channel rather than a shared flow, again per [DocumentInbox]: nothing may be
 * lost while the library is starting, and a menu item pressed once must not
 * replay into a resubscribe and open two file panels.
 *
 * Collected by whatever lives as long as the menu does, which is the shell
 * rather than any screen - see `DesktopShell`. That is the half [DocumentInbox]
 * gets right by being collected on a ViewModel scope, and the half this is easy
 * to get wrong: a channel hands each command to exactly one collector, so a
 * collector that comes and goes with a screen does not miss commands, it banks
 * them and carries them out whenever it next appears.
 */
class AppCommands {

    /**
     * Dropping the oldest rather than suspending: this is called from a menu
     * item's own callback, which cannot wait. A reader who has pressed further
     * ahead than this is holding down a shortcut, and the last press is the one
     * they mean.
     */
    private val requested = Channel<AppCommand>(
        capacity = CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val commands: Flow<AppCommand> = requested.receiveAsFlow()

    fun request(command: AppCommand) {
        requested.trySend(command)
    }

    private companion object {
        const val CAPACITY = 8
    }
}
