package io.lukia

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.lukia.core.infrastructure.AppCommand
import io.lukia.core.infrastructure.AppCommands
import io.lukia.core.infrastructure.DesktopWindowBounds
import io.lukia.core.infrastructure.MinHeight
import io.lukia.core.infrastructure.MinWidth
import io.lukia.core.infrastructure.SavedWindow
import io.lukia.core.infrastructure.isMacOs
import io.lukia.di.commonModule
import io.lukia.di.platformModule
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.koin.core.context.startKoin
import java.awt.Dimension

private object Resources

private val windowIcon: Painter by lazy {
    val icon = checkNotNull(Resources::class.java.getResourceAsStream("/icons/lukia.png")) {
        "Missing window icon: /icons/lukia.png"
    }
    BitmapPainter(icon.use { it.readAllBytes() }.decodeToImageBitmap())
}

/**
 * What the window opens at when there is nothing stored.
 *
 * Wider than Compose's own 800x600 default, and chosen rather than inherited:
 * 800 lands in the Medium width class, which is the one meant for a tablet or a
 * half-screen window. A reading application opening at tablet proportions on a
 * desktop is one that never shows the Expanded metrics the design system defines
 * for it unless the reader drags it out by hand - see `LukiaDimens`.
 */
private val DefaultWindowSize = DpSize(1280.dp, 860.dp)

/** How long the window has to sit still before where it is gets written down. */
private const val BoundsSettleMillis = 500L

fun main() {
    val koin = startKoin {
        modules(
            commonModule,
            platformModule,
        )
    }.koin

    val bounds = koin.get<DesktopWindowBounds>()
    val commands = koin.get<AppCommands>()
    val saved = bounds.saved()

    application {
        val windowState = rememberWindowState(
            size = saved?.let { DpSize(it.width.dp, it.height.dp) } ?: DefaultWindowSize,
            position = saved?.positionOrCentred() ?: WindowPosition.PlatformDefault,
            placement = if (saved?.maximized == true) {
                WindowPlacement.Maximized
            } else {
                WindowPlacement.Floating
            },
        )

        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = "Lukia",
            icon = windowIcon,
            // What the menu bar would otherwise be carrying, on the desktops
            // that no longer have one - see [LukiaMenuBar].
            onKeyEvent = { event -> !isMacOs && commands.requestedBy(event) },
        ) {
            // Compose has no parameter for a minimum, so it is set on the AWT
            // window underneath. Below this the grid has no room for two columns
            // and the reader's floating chrome starts overlapping itself.
            LaunchedEffect(window) {
                window.minimumSize = Dimension(MinWidth, MinHeight)
            }

            RememberWhereTheWindowWas(windowState, bounds)

            if (isMacOs) LukiaMenuBar(commands)

            App()
        }
    }
}

/**
 * Writes the window down as the reader leaves it.
 *
 * Debounced rather than written on every frame: dragging a window edge changes
 * the size continuously, and the store rewrites its whole file on each change.
 * Waiting for the drag to stop turns a few hundred writes into one.
 *
 * A maximised window records the size it would go back to rather than the size
 * it currently fills - restoring a *floating* window the size of the whole
 * screen is not what maximising it asked for.
 */
@Composable
private fun RememberWhereTheWindowWas(state: WindowState, bounds: DesktopWindowBounds) {
    LaunchedEffect(state, bounds) {
        snapshotFlow { WindowShape(state.size, state.position, state.placement) }
            .distinctUntilChanged()
            .debounce(BoundsSettleMillis)
            .collect { shape ->
                if (shape.placement == WindowPlacement.Fullscreen) return@collect

                bounds.save(
                    width = shape.size.width.value.toInt(),
                    height = shape.size.height.value.toInt(),
                    x = shape.position.x.value.toInt(),
                    y = shape.position.y.value.toInt(),
                    maximized = shape.placement == WindowPlacement.Maximized,
                )
            }
    }
}

private data class WindowShape(
    val size: DpSize,
    val position: WindowPosition,
    val placement: WindowPlacement,
)

/**
 * The window's own menus, on the one desktop where they belong at the top of the
 * screen rather than at the top of the window.
 *
 * macOS keeps this in its own menu bar, where every application has one and a
 * reader looks up for it. Windows and Linux draw it as a strip inside the
 * window - a row of chrome above a library that already carries search, the
 * statistics and its own actions menu, saying "File" and nothing else. That is
 * the whole menu bar's width spent on one word, over an app whose every other
 * surface is a floating panel.
 *
 * So it is built on macOS only. Nothing is lost with it: adding books, adding a
 * folder and the settings are all in the library's actions menu, and the
 * shortcuts that were the menu's other job are handled by the window itself -
 * see [requestedBy].
 *
 * Settings sits under File rather than in the application menu it would occupy
 * on macOS, because that menu belongs to the JVM and is not Compose's to add to.
 * The shortcut is the conventional one either way, which is the half a reader's
 * fingers actually know.
 */
@Composable
private fun FrameWindowScope.LukiaMenuBar(commands: AppCommands) {
    MenuBar {
        Menu("File", mnemonic = 'F') {
            Item(
                "Add Books…",
                shortcut = shortcut(Key.O),
                onClick = { commands.request(AppCommand.AddBooks) },
            )
            Item(
                "Add Folder…",
                shortcut = shortcut(Key.O, shift = true),
                onClick = { commands.request(AppCommand.AddFolder) },
            )
            Separator()
            Item(
                "Settings…",
                shortcut = shortcut(Key.Comma),
                onClick = { commands.request(AppCommand.OpenSettings) },
            )
        }
    }
}

/** Command, which is the modifier of the one platform that still carries this menu. */
private fun shortcut(key: Key, shift: Boolean = false): KeyShortcut =
    KeyShortcut(key, meta = true, shift = shift)

/**
 * The same three shortcuts the menu declares, read off the window instead.
 *
 * Windows and Linux have no menu bar to hang them on - see [LukiaMenuBar] - and
 * a reader who has learnt Ctrl+O in every other application should not find it
 * dead here because of where this app decided to put its chrome.
 *
 * Control only: the Command half of [shortcut] is for the platform that still
 * has the menu. Read after the composition rather than before it, so a shortcut
 * never fires out of a field the reader is typing in.
 *
 * Returns whether the press was ours, which is what stops it travelling on.
 */
private fun AppCommands.requestedBy(event: KeyEvent): Boolean {
    if (event.type != KeyEventType.KeyDown || !event.isCtrlPressed) return false

    val command = when (event.key) {
        Key.O -> if (event.isShiftPressed) AppCommand.AddFolder else AppCommand.AddBooks
        Key.Comma -> AppCommand.OpenSettings
        else -> null
    } ?: return false

    request(command)

    return true
}

/**
 * Where the window was, unless that is nowhere useful.
 *
 * A stored position is only good for the display arrangement it was written on.
 * Restored onto a monitor that has since been unplugged, the window opens with
 * its title bar off-screen and cannot be dragged back - so an absent position
 * means the platform places it, which is what a first run gets.
 */
private fun SavedWindow.positionOrCentred(): WindowPosition {
    val left = x
    val top = y

    return if (left == null || top == null) {
        WindowPosition.PlatformDefault
    } else {
        WindowPosition(left.dp, top.dp)
    }
}
