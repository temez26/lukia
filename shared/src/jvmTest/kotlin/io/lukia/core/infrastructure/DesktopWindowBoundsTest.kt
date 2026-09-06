package io.lukia.core.infrastructure

import io.lukia.core.domain.storage.FakeSettingsStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Where the desktop window opens.
 *
 * The rules worth pinning are all about what happens when the answer is not
 * usable, because that is the case a reader cannot recover from on their own: a
 * window restored off the edge of the displays they have now is a window with no
 * title bar to drag.
 */
class DesktopWindowBoundsTest {

    private val settings = FakeSettingsStore()

    /** A laptop, and a wider monitor sitting to the right of it. */
    private val twoScreens = listOf(
        ScreenArea(x = 0, y = 0, width = 1440, height = 900),
        ScreenArea(x = 1440, y = 0, width = 2560, height = 1440),
    )

    private val bounds = DesktopWindowBounds(settings) { twoScreens }

    @Test
    fun `a first run has nothing to restore`() {
        assertNull(bounds.saved())
    }

    @Test
    fun `a window comes back where it was left`() {
        bounds.save(width = 1400, height = 900, x = 120, y = 80, maximized = false)

        val saved = bounds.saved()

        assertEquals(1400, saved?.width)
        assertEquals(900, saved?.height)
        assertEquals(120, saved?.x)
        assertEquals(80, saved?.y)
        assertEquals(false, saved?.maximized)
    }

    @Test
    fun `being maximised is remembered`() {
        bounds.save(width = 1400, height = 900, x = 0, y = 0, maximized = true)

        assertEquals(true, bounds.saved()?.maximized)
    }

    @Test
    fun `a window too small to use is not stored`() {
        bounds.save(width = 40, height = 30, x = 0, y = 0, maximized = false)

        assertNull(bounds.saved())
    }

    @Test
    fun `a stored size too small to use is not restored`() {
        // Written by an older build, or by a run on a machine with a display
        // arrangement this one no longer has.
        settings.putString("window.width", "40")
        settings.putString("window.height", "30")

        assertNull(bounds.saved())
    }

    @Test
    fun `nonsense in the store is treated as nothing stored`() {
        settings.putString("window.width", "wide")
        settings.putString("window.height", "900")

        assertNull(bounds.saved())
    }

    @Test
    fun `a size with no position is still worth restoring`() {
        settings.putString("window.width", "1400")
        settings.putString("window.height", "900")

        val saved = bounds.saved()

        assertTrue(saved != null)
        assertNull(saved.x)
        assertNull(saved.y)
    }

    @Test
    fun `a window left on a monitor that has since been unplugged opens where it can be seen`() {
        // Written while the second monitor was attached, read back on the
        // laptop alone: a title bar at x=2000 is a window with nothing to drag.
        val laptopOnly = DesktopWindowBounds(settings) { twoScreens.take(1) }

        bounds.save(width = 1400, height = 900, x = 2000, y = 200, maximized = false)

        val saved = laptopOnly.saved()

        assertEquals(1400, saved?.width)
        assertNull(saved?.x)
        assertNull(saved?.y)
    }

    @Test
    fun `a window on a second monitor comes back to it while it is still there`() {
        bounds.save(width = 1400, height = 900, x = 2000, y = 200, maximized = false)

        assertEquals(2000, bounds.saved()?.x)
        assertEquals(200, bounds.saved()?.y)
    }

    @Test
    fun `a title bar dragged off the top of the screen is not restored there`() {
        // macOS will not let this happen; Windows and Linux both will, and a
        // window whose bar is above the desktop has no bar at all.
        bounds.save(width = 1400, height = 900, x = 100, y = -400, maximized = false)

        assertNull(bounds.saved()?.y)
    }

    @Test
    fun `a title bar below the bottom of the screen is not restored there`() {
        bounds.save(width = 1400, height = 900, x = 100, y = 890, maximized = false)

        assertNull(bounds.saved()?.y)
    }

    @Test
    fun `a sliver of a window at the edge of a display is not enough to grab`() {
        // Sixty pixels of title bar on screen, the rest off the right of it.
        bounds.save(width = 1400, height = 900, x = 3940, y = 200, maximized = false)

        assertNull(bounds.saved()?.x)
    }

    @Test
    fun `a window hanging off the bottom of a display is left where it was`() {
        // Only the body is off-screen, and the reader can drag it back by the
        // bar - so this is not a position worth overruling.
        bounds.save(width = 1400, height = 900, x = 100, y = 800, maximized = false)

        assertEquals(100, bounds.saved()?.x)
        assertEquals(800, bounds.saved()?.y)
    }

    @Test
    fun `a headless machine has no arrangement to judge a position against`() {
        val headless = DesktopWindowBounds(settings) { emptyList() }

        bounds.save(width = 1400, height = 900, x = 9000, y = 9000, maximized = false)

        assertEquals(9000, headless.saved()?.x)
    }
}
