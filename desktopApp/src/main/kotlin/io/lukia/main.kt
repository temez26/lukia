package io.lukia

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.awt.ComposeWindow
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
import io.lukia.core.infrastructure.deliverIncomingDocuments
import io.lukia.core.infrastructure.isMacOs
import io.lukia.di.commonModule
import io.lukia.di.platformModule
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.koin.core.context.startKoin
import java.awt.Dimension
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetDropEvent
import java.io.File

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

/**
 * [args] are the books the desktop was asked to open with - a file double-clicked
 * in a file manager, or one named on the command line. Every desktop hands an
 * application its documents this way, which is what makes the file associations
 * declared in `desktopApp/build.gradle.kts` do anything at all.
 */
fun main(args: Array<String>) {
    val koin = startKoin {
        modules(
            commonModule,
            platformModule,
        )
    }.koin

    val bounds = koin.get<DesktopWindowBounds>()
    val commands = koin.get<AppCommands>()
    val saved = bounds.saved()

    // Queued before there is a composition, and taken by the library as soon as
    // there is one - see `DocumentInbox`, which exists for exactly this gap.
    deliverIncomingDocuments(args.map(::File))

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
            // The window is written down here as well as on the timer, because
            // the timer is deliberately half a second behind the reader - see
            // [RememberWhereTheWindowWas] - and dragging a window to where you
            // want it and then closing the app is not an unusual thing to do.
            // Without this that last drag is the one arrangement never kept.
            onCloseRequest = {
                bounds.remember(windowState.shape())
                exitApplication()
            },
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
                window.acceptBooksDroppedOnIt()
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
 * A maximised window records the size it would go back to, not the size it
 * fills: restoring a *floating* window the size of the screen is not what
 * maximising asked for.
 */
@Composable
private fun RememberWhereTheWindowWas(state: WindowState, bounds: DesktopWindowBounds) {
    LaunchedEffect(state, bounds) {
        snapshotFlow { state.shape() }
            .distinctUntilChanged()
            .debounce(BoundsSettleMillis)
            .collect(bounds::remember)
    }
}

private data class WindowShape(
    val size: DpSize,
    val position: WindowPosition,
    val placement: WindowPlacement,
)

private fun WindowState.shape(): WindowShape = WindowShape(size, position, placement)

/**
 * Stores one shape, unless it is a full-screen one or one that has no position
 * yet.
 *
 * A window the reader put into full screen has the size of the display rather
 * than a size they chose, and coming back out of it should find the window they
 * were using - so that shape is passed over and the last real one stands.
 *
 * A window still opening has [WindowPosition.PlatformDefault], whose coordinates
 * are unspecified rather than zero - storing those would park the window in the
 * top-left corner on behalf of a reader who never put it there.
 */
private fun DesktopWindowBounds.remember(shape: WindowShape) {
    if (shape.placement == WindowPlacement.Fullscreen) return
    if (!shape.position.isSpecified) return

    save(
        width = shape.size.width.value.toInt(),
        height = shape.size.height.value.toInt(),
        x = shape.position.x.value.toInt(),
        y = shape.position.y.value.toInt(),
        maximized = shape.placement == WindowPlacement.Maximized,
    )
}

/**
 * Books dragged onto the window from a file manager.
 *
 * What a desktop reader tries before looking for a menu, and the counterpart to
 * the mobile share sheet - the file lands in the same inbox either way.
 *
 * Set on the AWT window rather than through Compose's drag-and-drop, because
 * what is wanted is the whole window: a reader dropping a book on a reading app
 * is not aiming at a target inside it, and everything that is not a target would
 * otherwise refuse the drop.
 */
private fun ComposeWindow.acceptBooksDroppedOnIt() {
    dropTarget = object : DropTarget() {
        override fun drop(event: DropTargetDropEvent) {
            if (!event.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                event.rejectDrop()
                return
            }

            event.acceptDrop(DnDConstants.ACTION_COPY)

            val dropped = runCatching {
                @Suppress("UNCHECKED_CAST")
                event.transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
            }

            // Told before the books are read, and told the truth: what can fail
            // from here is reading a file, which the import reports on the shelf
            // rather than through a cursor that is already gone.
            event.dropComplete(dropped.isSuccess)

            deliverIncomingDocuments(dropped.getOrDefault(emptyList()))
        }
    }
}

/**
 * The window's own menus, on the one desktop where they belong at the top of the
 * screen rather than at the top of the window.
 *
 * macOS keeps this in its own menu bar, where a reader looks up for it. Windows
 * and Linux draw it as a strip inside the window - a whole bar's width saying
 * "File" and nothing else, over a library that already carries search, the
 * statistics and its own actions menu.
 *
 * So it is built on macOS only, and nothing is lost with it: adding books,
 * adding a folder and the settings are all in the library's actions menu, and
 * the shortcuts are handled by the window itself - see [requestedBy].
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
            Item(
                "Find…",
                shortcut = shortcut(Key.F),
                onClick = { commands.request(AppCommand.FindInLibrary) },
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
 * The same shortcuts the menu declares, read off the window instead.
 *
 * Windows and Linux have no menu bar to hang them on - see [LukiaMenuBar] - and
 * a reader who learnt Ctrl+O everywhere else should not find it dead here.
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
        Key.F -> AppCommand.FindInLibrary
        else -> null
    } ?: return false

    request(command)

    return true
}

/**
 * Where the window was, unless there is nowhere useful to put it.
 *
 * A stored position is only good for the display arrangement it was written on,
 * and [DesktopWindowBounds] is what decides whether this machine still has that
 * arrangement. An absent position is its way of saying no, and means the platform
 * places the window - which is what a first run gets.
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
