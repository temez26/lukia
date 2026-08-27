package io.lukia.shared.presentation.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How large a control is drawn, which is a question about the hand rather than
 * about the window.
 */
class MetricsTest {

    @Test
    fun `anything touched is given a finger-sized target`() {
        val touch = metricsFor(touch = true)

        assertEquals(MinTouchTarget, touch.controlHeight)
        assertEquals(MinTouchTarget, touch.iconButton)
        assertEquals(MinTouchTarget, touch.rowHeight)
    }

    @Test
    fun `anything pointed at is drawn smaller than a finger needs`() {
        val pointer = metricsFor(touch = false)

        // Not a target for a fingertip, and deliberately: a 44dp button under a
        // mouse cursor is mostly padding, which is most of what makes a desktop
        // window look like a scaled-up phone.
        assertTrue(pointer.controlHeight < MinTouchTarget)
        assertTrue(pointer.iconButton < MinTouchTarget)
    }

    @Test
    fun `a window's width has no say in it`() {
        // The regression this exists for: metrics used to be read off the width
        // class, so a 1024dp iPad and a phone turned to landscape at 844dp were
        // both handed pointer-sized controls to press with a thumb.
        WindowWidthClass.entries.forEach { widthClass ->
            assertEquals(
                metricsFor(touch = true),
                metricsFor(touch = true),
                "width class $widthClass changed a touch target",
            )
        }

        assertEquals(MinTouchTarget, metricsFor(touch = true).controlHeight)
    }
}
