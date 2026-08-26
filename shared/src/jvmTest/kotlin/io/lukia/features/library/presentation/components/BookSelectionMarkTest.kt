package io.lukia.features.library.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What the shelf's rocking costs while it is running.
 *
 * A forever animation is the one kind that has to be paid for on every frame
 * for as long as the mode lasts, and a shelf holds a hundred of them at once -
 * so where the animated value is read is not a detail here, it is the whole
 * cost. Read during composition it recomposes the card sixty times a second;
 * read in the layer that draws, nothing above the layer runs at all.
 */
@OptIn(ExperimentalTestApi::class)
class BookSelectionMarkTest {

    /**
     * Counted rather than measured, and counted across frames of the animation
     * rather than across a change: the angle moving is the point, and what must
     * not move with it is what each frame costs.
     *
     * It is also what lets a test reach idle while a shelf is being selected
     * from. A composition invalidated on every frame is never idle, and a
     * `waitForIdle` waiting for that one to settle waits until the suite gives
     * up on it.
     */
    @Test
    fun theRockingDoesNotRecomposeTheCardItRocks() = runComposeUiTest {
        var compositions = 0

        // Driven by hand, so the frames are this test's rather than the
        // machine's - the same count on a loaded CI runner as on an idle one.
        mainClock.autoAdvance = false

        setContent {
            compositions++

            val angle = jiggleAngle(active = true, seed = 0)

            Box(modifier = Modifier.size(48.dp).graphicsLayer { rotationZ = angle.value })
        }

        val onceItIsOnScreen = compositions
        mainClock.advanceTimeBy(milliseconds = 500)

        assertEquals(onceItIsOnScreen, compositions, "the shelf recomposed to rock")
    }
}
