package io.lukia.shared.presentation.theme

import io.lukia.core.domain.AppMotion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What the motion presets add up to.
 *
 * The numbers themselves are taste and are not asserted on. What is asserted is
 * that the three stay in the order they claim to be in - a preset called Calm
 * that moved further than Standard would be a lie told in a settings screen -
 * and that Standard is the neutral one everything else is measured against.
 */
class MotionStyleTest {

    @Test
    fun standardIsTheOneThatChangesNothing() {
        // Every reader of MotionStyle multiplies by these, so Standard has to be
        // the identity or the app as designed is not what Standard produces.
        val standard = motionStyleFor(AppMotion.Standard)

        assertEquals(1f, standard.stretch)
        assertEquals(1f, standard.travel)
        assertTrue(standard.flourishes)
    }

    @Test
    fun theThreePresetsTravelInTheOrderTheyAreOffered() {
        val distances = AppMotion.entries.map { motionStyleFor(it).travel }

        assertEquals(distances.sorted(), distances, "presets are offered out of order")
    }

    @Test
    fun onlyTheCalmestGivesUpItsFlourishes() {
        assertFalse(motionStyleFor(AppMotion.Calm).flourishes)
        assertTrue(motionStyleFor(AppMotion.Playful).flourishes)
    }

    @Test
    fun theCalmestIsShorterRatherThanSlower() {
        // Reduced motion is not slow motion. A reader who does not want to watch
        // the app move wants it over with, not eased out.
        assertTrue(motionStyleFor(AppMotion.Calm).stretch < 1f)
        assertTrue(motionStyleFor(AppMotion.Playful).stretch > 1f)
    }
}
