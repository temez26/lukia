package io.lukia.core.infrastructure

import io.lukia.core.domain.FakeSettingsStore
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
    private val bounds = DesktopWindowBounds(settings)

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
}
