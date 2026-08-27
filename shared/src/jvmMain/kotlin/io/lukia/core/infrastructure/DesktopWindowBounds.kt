package io.lukia.core.infrastructure

import io.lukia.core.domain.SettingsStore

/**
 * Where the desktop window was last left, and how big.
 *
 * Kept because the alternative is what the app did before: every launch opened
 * Compose's default 800x600 in the middle of the screen, whatever the reader had
 * arranged the session before. On a large monitor that is also the wrong size in
 * a way that carries through the whole app - it lands in the Medium width class,
 * so the desktop metrics the design system defines for Expanded and Large
 * windows were only ever reached by dragging the window out by hand, every time.
 *
 * Stored through [SettingsStore] rather than in a file of its own: it is a
 * handful of short values that have to be there before the first frame, which is
 * exactly what that store is for.
 *
 * Nothing here is trusted on the way back in. A stored position is only good for
 * the display arrangement it was written on, and a window restored onto a
 * monitor that has since been unplugged is a window the reader cannot reach - so
 * [DesktopWindowBounds.saved] answers null for anything that does not look like
 * a usable window, and the caller opens where it would have anyway.
 */
class DesktopWindowBounds(
    private val settings: SettingsStore,
) {

    /** What was last stored, or null on a first run or an unusable answer. */
    fun saved(): SavedWindow? {
        val width = settings.number(WIDTH) ?: return null
        val height = settings.number(HEIGHT) ?: return null

        if (width < MinWidth || height < MinHeight) return null

        return SavedWindow(
            width = width,
            height = height,
            x = settings.number(X),
            y = settings.number(Y),
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

/** A stored window. [x] and [y] are absent for one that was never moved. */
data class SavedWindow(
    val width: Int,
    val height: Int,
    val x: Int?,
    val y: Int?,
    val maximized: Boolean,
)

/**
 * The smallest the window may be made.
 *
 * Not a matter of taste: below this the library's grid has no room for two
 * columns and the reader's chrome starts overlapping itself. Compose has no
 * parameter for a minimum, so the caller sets it on the AWT window directly.
 */
const val MinWidth = 640
const val MinHeight = 480
