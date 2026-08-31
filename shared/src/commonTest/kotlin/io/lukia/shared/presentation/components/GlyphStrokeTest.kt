package io.lukia.shared.presentation.components

import androidx.compose.ui.unit.dp
import io.lukia.shared.presentation.theme.MinTouchTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How heavily the drawn icon set is stroked, which is a question about the size
 * the mark is actually drawn at rather than about the mark.
 */
class GlyphStrokeTest {

    /** The slot most of the set is asked for: [io.lukia.shared.presentation.theme.LukiaMetrics.icon]. */
    private val commonSlot = 20.dp

    @Test
    fun theFamilyKeepsTheWeightItHadAtTheSizeItWasDrawnFor() {
        // The literal every glyph used to carry. The ratio is set from it, so
        // nothing about the app's most common icon changed when the stroke
        // started scaling.
        assertEquals(2.dp, glyphStrokeFor(commonSlot))
    }

    @Test
    fun oneGlyphIsTheSameWeightWhereverItIsDrawn() {
        // The regression this exists for. A fixed 2dp stroke is an eighth of a
        // 15dp glyph and a twelfth of a 24dp one, so the same icon read as bold
        // in a menu and as hairline on a tab bar.
        val slots = listOf(15.dp, 17.dp, 18.dp, 20.dp, 24.dp)

        val proportions = slots.map { slot -> glyphStrokeFor(slot).value / slot.value }

        proportions.forEach { proportion ->
            assertTrue(
                proportion in 0.09f..0.11f,
                "a glyph in one of the app's own slots is stroked at $proportion of its size",
            )
        }
    }

    @Test
    fun theLightWeightIsLighterAtEverySizeTheSetIsDrawnAt() {
        listOf(15.dp, 20.dp, 24.dp).forEach { slot ->
            assertTrue(
                glyphStrokeFor(slot, GlyphWeight.Light) < glyphStrokeFor(slot, GlyphWeight.Regular),
                "the light weight is not lighter at $slot",
            )
        }
    }

    @Test
    fun aMarkTooSmallToScaleStillHasAStrokeToSeeIt() {
        // Nothing in the app draws a glyph this small today, and the floor is
        // what keeps one that does from disappearing into the pixel grid.
        assertTrue(glyphStrokeFor(8.dp) >= 1.25.dp)
    }

    @Test
    fun aMarkDrawnLargeDoesNotTurnIntoAWoodcut() {
        // An empty state draws its mark at something nearer a button than an
        // icon, and an unclamped ratio would stroke it at 4dp.
        assertTrue(glyphStrokeFor(MinTouchTarget * 2) <= 2.4.dp)
    }
}
