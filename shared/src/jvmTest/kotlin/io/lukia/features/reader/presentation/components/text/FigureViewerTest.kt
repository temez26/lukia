package io.lukia.features.reader.presentation.components.text

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.lukia.features.reader.domain.text.TextBlock
import io.lukia.shared.presentation.theme.LukiaTheme
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * That a picture in a book can be looked at properly.
 *
 * A plate is laid out to the measure of a page of prose, and a diagram or a
 * table fitted into one is often no longer readable. The way out is the figure
 * itself: pressed, it is lifted onto the window - see [FigureViewer].
 */
@OptIn(ExperimentalTestApi::class)
class FigureViewerTest {

    private fun png(width: Int, height: Int): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val output = ByteArrayOutputStream()

        ImageIO.write(image, "png", output)

        return output.toByteArray()
    }

    /** Draws one figure in a window, and waits for it to be decoded. */
    private fun ComposeUiTest.showFigure() {
        setContent {
            LukiaTheme(darkTheme = false, touch = false) {
                Box(modifier = Modifier.size(600.dp, 800.dp)) {
                    FigureBlock(TextBlock.Figure(bytes = png(40, 20), description = "plate"))
                }
            }
        }

        waitUntil {
            onAllNodesWithContentDescription("plate").fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Drawn at 40dp in a 600dp column, and no use at all without a way in. */
    @Test
    fun aFigureCanBePressed() = runComposeUiTest {
        showFigure()

        onNodeWithContentDescription("plate").assertHasClickAction()
    }

    /**
     * The viewer is left in the tree unopened so that closing it can be animated
     * - see [FigureViewer] - and a book carries hundreds of these, so an unopened
     * one has to cost nothing at all.
     */
    @Test
    fun anUnopenedViewerDrawsNothing() = runComposeUiTest {
        showFigure()

        assertEquals(
            1,
            onAllNodesWithContentDescription("plate").fetchSemanticsNodes().size,
            "An unopened viewer should draw no second copy of the figure",
        )

        onAllNodesWithContentDescription("Close").assertCountEquals(0)
    }

    @Test
    fun pressingAFigureOpensItOverTheBook() = runComposeUiTest {
        showFigure()

        onNodeWithContentDescription("plate").performClick()

        // The one on the page and the one held up over it. The viewer keeps the
        // book's own copy composed underneath, which is what lets it be closed
        // back onto the paragraph it came from.
        waitUntil {
            onAllNodesWithContentDescription("plate").fetchSemanticsNodes().size == 2
        }
    }

    @Test
    fun theViewerCanBeClosedAgain() = runComposeUiTest {
        showFigure()

        onNodeWithContentDescription("plate").performClick()

        waitUntil {
            onAllNodesWithContentDescription("plate").fetchSemanticsNodes().size == 2
        }

        onNodeWithContentDescription("Close").performClick()

        waitUntil {
            onAllNodesWithContentDescription("plate").fetchSemanticsNodes().size == 1
        }

        assertEquals(
            1,
            onAllNodesWithContentDescription("plate").fetchSemanticsNodes().size,
            "Closing the viewer should leave only the figure on the page",
        )
    }
}
