package io.lukia.features.reader.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.lukia.features.reader.domain.TextBlock
import io.lukia.shared.presentation.theme.LukiaTheme
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * How large an illustration is drawn.
 *
 * Books carry ornaments, symbols and inline marks of a few dozen pixels
 * alongside their plates, and the rule has to serve both.
 */
@OptIn(ExperimentalTestApi::class)
class FigureBlockTest {

    private val column = 600.dp

    private fun png(width: Int, height: Int): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val output = ByteArrayOutputStream()

        ImageIO.write(image, "png", output)

        return output.toByteArray()
    }

    private fun figure(width: Int, height: Int) = TextBlock.Figure(
        bytes = png(width, height),
        description = "plate",
    )

    @Test
    fun anIllustrationSmallerThanTheMeasureIsDrawnAtItsOwnSize() = runComposeUiTest {
        setContent {
            LukiaTheme {
                Box(modifier = Modifier.size(column, 800.dp)) {
                    FigureBlock(figure(width = 40, height = 20))
                }
            }
        }

        waitUntil {
            onAllNodesWithContentDescription("plate").fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithContentDescription("plate").assertWidthIsEqualTo(40.dp)
    }

    /** A 16-pixel ornament stretched to the measure is a full-width picture of nothing. */
    @Test
    fun anOrnamentStaysAnOrnament() = runComposeUiTest {
        setContent {
            LukiaTheme {
                Box(modifier = Modifier.size(column, 800.dp)) {
                    FigureBlock(figure(width = 16, height = 16))
                }
            }
        }

        waitUntil {
            onAllNodesWithContentDescription("plate").fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithContentDescription("plate").assertWidthIsEqualTo(16.dp)
    }

    @Test
    fun anIllustrationLargerThanTheMeasureIsShrunkToFitIt() = runComposeUiTest {
        setContent {
            LukiaTheme {
                Box(modifier = Modifier.size(column, 800.dp)) {
                    FigureBlock(figure(width = 2000, height = 1000))
                }
            }
        }

        waitUntil {
            onAllNodesWithContentDescription("plate").fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithContentDescription("plate").assertWidthIsEqualTo(column)
    }

    /**
     * A plate taller than the page it was put on is held to the page rather than
     * running off the bottom of it - and is still never widened past its own
     * size to make up for it.
     */
    @Test
    fun anIllustrationTallerThanItsPageIsHeldToThePage() = runComposeUiTest {
        setContent {
            LukiaTheme {
                Box(modifier = Modifier.size(column, 800.dp)) {
                    FigureBlock(figure(width = 400, height = 1200), maxHeight = 300.dp)
                }
            }
        }

        waitUntil {
            onAllNodesWithContentDescription("plate").fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithContentDescription("plate").assertHeightIsEqualTo(300.dp)
        onNodeWithContentDescription("plate").assertWidthIsEqualTo(400.dp)
    }
}
