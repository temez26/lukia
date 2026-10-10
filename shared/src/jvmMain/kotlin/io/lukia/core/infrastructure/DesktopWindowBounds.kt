package io.lukia.core.infrastructure

import io.lukia.core.domain.storage.SettingsStore
import java.awt.GraphicsEnvironment

/**
 * Where the desktop window was last left, and how big.
 *
 * Without this every launch opened Compose's default 800x600 in the middle of
 * the screen, whatever the reader had arranged the session before. On a large
 * monitor that is also the wrong size in a way that carries through the app - it
 * lands in the Medium width class, so the Expanded and Large desktop metrics
 * were only reached by dragging the window out by hand, every time.
 *
 * Stored through [SettingsStore] rather than a file of its own: a handful of
 * short values that have to be there before the first frame.
 *
 * Nothing is trusted on the way back in. A stored position is only good for the
 * display arrangement it was written on, so [saved] answers with no position for
 * one that no longer lands on a display this machine has, and the caller opens
 * where it would have on a first run.
 *
 * [screens] is a parameter so the rule above can be tested against an
 * arrangement rather than against whatever monitors the test machine has.
 */
class DesktopWindowBounds(
    private val settings: SettingsStore,
    private val screens: () -> List<ScreenArea> = ::attachedScreens,
) {

    /** What was last stored, or null on a first run or an unusable answer. */
    fun saved(): SavedWindow? {
        val width = settings.number(WIDTH) ?: return null
        val height = settings.number(HEIGHT) ?: return null

        if (width < MinWidth || height < MinHeight) return null

        val x = settings.number(X)
        val y = settings.number(Y)
        val reachable = x != null && y != null && isReachable(x, y, width, screens())

        return SavedWindow(
            width = width,
            height = height,
            x = x.takeIf { reachable },
            y = y.takeIf { reachable },
            maximized = settings.getString(MAXIMIZED).toBoolean(),
        )
    }

    /**
     * Records the window as it is now.
     *
     * A maximised window stores the size it would go back to rather than the
     * size it currently fills, which is why the caller passes both: restoring a
     * *floating* window the size of the whole screen is not what maximising it
     * asked for.
     */
    fun save(width: Int, height: Int, x: Int, y: Int, maximized: Boolean) {
        if (width < MinWidth || height < MinHeight) return

        settings.putString(WIDTH, width.toString())
        settings.putString(HEIGHT, height.toString())
        settings.putString(X, x.toString())
        settings.putString(Y, y.toString())
        settings.putString(MAXIMIZED, maximized.toString())
    }

    private fun SettingsStore.number(key: String): Int? = getString(key)?.toIntOrNull()

    private companion object {
        const val WIDTH = "window.width"
        const val HEIGHT = "window.height"
        const val X = "window.x"
        const val Y = "window.y"
        const val MAXIMIZED = "window.maximized"
    }
}

/** A stored window. [x] and [y] are absent for one that cannot be opened there. */
data class SavedWindow(
    val width: Int,
    val height: Int,
    val x: Int?,
    val y: Int?,
    val maximized: Boolean,
)

/** One display, as a rectangle in the coordinate space the desktop shares between them. */
data class ScreenArea(val x: Int, val y: Int, val width: Int, val height: Int)

/**
 * Whether a window opened at this position could be reached.
 *
 * The title bar is the whole test, being the only part of a window that can move
 * the rest: a body hanging off the bottom of a display is fixed in a second, and
 * a title bar on a monitor since unplugged is a window nobody can touch. Not a
 * rare arrangement - a laptop docked to a second screen and then undocked is the
 * ordinary way to end a working day.
 *
 * Enough of the bar has to land on one display for there to be something to take
 * hold of: a sliver at the far edge is a window hidden rather than restored.
 *
 * An empty list of screens is a headless JVM rather than a machine with no
 * monitors, so there is nothing to check against and the position stands.
 */
private fun isReachable(x: Int, y: Int, width: Int, screens: List<ScreenArea>): Boolean {
    if (screens.isEmpty()) return true

    return screens.any { screen ->
        val visibleWidth = minOf(x + width, screen.x + screen.width) - maxOf(x, screen.x)

        visibleWidth >= GrabWidth &&
            y >= screen.y &&
            y <= screen.y + screen.height - GrabHeight
    }
}

/**
 * The displays attached to this machine, or none at all when there is no screen
 * to ask about - a headless JVM, which is what the tests and any build server
 * run in.
 */
private fun attachedScreens(): List<ScreenArea> {
    if (GraphicsEnvironment.isHeadless()) return emptyList()

    return runCatching {
        GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.map { device ->
            val bounds = device.defaultConfiguration.bounds

            ScreenArea(bounds.x, bounds.y, bounds.width, bounds.height)
        }
    }.getOrDefault(emptyList())
}

/**
 * The smallest the window may be made.
 *
 * Not a matter of taste: below this the library's grid has no room for two
 * columns and the reader's chrome starts overlapping itself. Compose has no
 * parameter for a minimum, so the caller sets it on the AWT window directly.
 */
const val MinWidth = 640
const val MinHeight = 480

/** How much of a window's title bar has to be on a display to count as reachable. */
private const val GrabWidth = 120
private const val GrabHeight = 32
