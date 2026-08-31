package io.lukia.shared.presentation.theme

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Which bucket a window falls into, on both axes.
 *
 * The pair matters more than either alone: a phone in landscape answers the same
 * width question a small tablet does, and only the height tells them apart.
 */
class WindowClassTest {

    @Test
    fun aPhoneHeldUprightIsCompactOnBothAxes() {
        assertEquals(WindowWidthClass.Compact, WindowWidthClass.fromWidth(390.dp))
        assertEquals(WindowHeightClass.Medium, WindowHeightClass.fromHeight(844.dp))
    }

    @Test
    fun aPhoneTurnedOnItsSideIsAsWideAsADesktopAndShorterThanAnything() {
        // The case the height class exists for. On width alone this phone is
        // Expanded - the same answer a comfortably sized desktop window gets -
        // so everything measured against it grows into vertical room that a
        // 390dp-tall window does not have. Only the height tells the two apart.
        assertEquals(WindowWidthClass.Expanded, WindowWidthClass.fromWidth(844.dp))
        assertEquals(WindowWidthClass.Expanded, WindowWidthClass.fromWidth(1024.dp))

        assertEquals(WindowHeightClass.Compact, WindowHeightClass.fromHeight(390.dp))
        assertEquals(WindowHeightClass.Expanded, WindowHeightClass.fromHeight(1366.dp))
    }

    @Test
    fun theBucketsMeetWithoutAGapOrAnOverlap() {
        assertEquals(WindowHeightClass.Compact, WindowHeightClass.fromHeight(479.dp))
        assertEquals(WindowHeightClass.Medium, WindowHeightClass.fromHeight(480.dp))
        assertEquals(WindowHeightClass.Medium, WindowHeightClass.fromHeight(899.dp))
        assertEquals(WindowHeightClass.Expanded, WindowHeightClass.fromHeight(900.dp))
    }
}
