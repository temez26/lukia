package io.lukia.shared.presentation.components.launch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The launch animation's timeline.
 *
 * What it looks like is a matter of taste and is not asserted on. What is
 * asserted is the order of events, because that is what makes the opening read
 * as one movement rather than as three effects that happen to overlap: the mark
 * is fully there before anything leaves, the sparkles are gone before the
 * handover, and the handover is finished when the clock is.
 */
class LaunchTimingTest {

    @Test
    fun theMarkHasArrivedLongBeforeItStartsToLeave() {
        assertEquals(0f, LaunchTiming.arrival(0f))
        assertEquals(1f, LaunchTiming.arrival(LaunchTiming.ArrivalMillis.toFloat()))
        assertTrue(LaunchTiming.ArrivalMillis < LaunchTiming.HandoffMillis)
    }

    @Test
    fun theFanIsFullyOpenWellBeforeTheMarkLeaves() {
        // The mark has to be seen as itself - open, settled, the icon on the
        // launcher - before it dissolves. A fan still opening into the handover
        // would read as the animation being cut off rather than finished.
        assertEquals(0f, LaunchTiming.fan(0f))
        assertEquals(0f, LaunchTiming.fan(LaunchTiming.FanStartMillis.toFloat()))
        assertEquals(
            1f,
            LaunchTiming.fan((LaunchTiming.FanStartMillis + LaunchTiming.FanMillis).toFloat()),
        )
        assertTrue(
            LaunchTiming.FanStartMillis + LaunchTiming.FanMillis < LaunchTiming.HandoffMillis,
        )
    }

    @Test
    fun theAppBelowStartsArrivingBeforeTheMarkLeaves() {
        // The overlap is the whole design: it is what lets the library finish
        // loading, swap its spinner for a grid and settle its first rows behind
        // something opaque. Lose it and the handover uncovers an empty screen
        // that then assembles itself in front of the reader.
        assertTrue(
            LaunchTiming.ContentArrivalMillis < LaunchTiming.HandoffMillis,
            "content arrives at ${LaunchTiming.ContentArrivalMillis}, handover at " +
                "${LaunchTiming.HandoffMillis}",
        )
    }

    @Test
    fun theAppFinishesGrowingAfterTheMarkHasGone() {
        // Both are travelling the same way through the handover, and the app is
        // the one still moving when the mark is no longer there to be compared
        // against - which is what stops the two reading as one abrupt event.
        assertTrue(LaunchTiming.ExpandMillis > LaunchTiming.DissolveMillis)
        assertEquals(0f, LaunchTiming.expansion(LaunchTiming.HandoffMillis.toFloat()))
        assertEquals(1f, LaunchTiming.expansion(LaunchTiming.TotalMillis.toFloat()))
    }

    @Test
    fun nothingDissolvesUntilTheHandover() {
        assertEquals(0f, LaunchTiming.dissolve(0f))
        assertEquals(0f, LaunchTiming.dissolve(LaunchTiming.HandoffMillis.toFloat()))
        assertEquals(1f, LaunchTiming.dissolve(LaunchTiming.TotalMillis.toFloat()))
    }

    @Test
    fun everySparkleIsGoneBeforeTheHandover() {
        // A glint still fading while the screen changes underneath it is the one
        // thing that would give the effect away as two animations.
        Sparkles.forEach { sparkle ->
            assertTrue(
                sparkle.endMillis <= LaunchTiming.HandoffMillis,
                "sparkle at ${sparkle.angleDegrees} outlives the handover",
            )
        }
    }

    @Test
    fun aSparkleIsInvisibleOutsideItsOwnWindow() {
        val sparkle = Sparkles.first()

        assertEquals(0f, sparkleProgress(0f, sparkle))
        assertEquals(0f, sparkleProgress(sparkle.endMillis.toFloat(), sparkle))
        assertEquals(
            0.5f,
            sparkleProgress(sparkle.startMillis + SparkleLifeMillis / 2f, sparkle),
        )
    }

    @Test
    fun theWholeOpeningIsShortEnoughToNotBeWaitedOut() {
        // Long enough to be watched, short enough that nobody waits for it. The
        // exact number is taste; the ceiling is not.
        assertTrue(LaunchTiming.TotalMillis <= 1500, "launch takes ${LaunchTiming.TotalMillis}ms")
    }
}
