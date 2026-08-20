package io.lukia.features.reader.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.features.reader.domain.FigureSize
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

    /** Draws one figure in a column of [column] and waits for it to be decoded. */
    private fun ComposeUiTest.showFigure(
        width: Int,
        height: Int,
        maxHeight: Dp = Dp.Unspecified,
        size: FigureSize = FigureSize.Normal,
    ) {
        setContent {
            LukiaTheme {
                Box(modifier = Modifier.size(column, 800.dp)) {
                    FigureBlock(figure(width, height), maxHeight = maxHeight, size = size)
                }
            }
        }

        waitUntil {
            onAllNodesWithContentDescription("plate").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun anIllustrationSmallerThanTheMeasureIsDrawnAtItsOwnSize() = runComposeUiTest {
        showFigure(width = 40, height = 20)

        onNodeWithContentDescription("plate").assertWidthIsEqualTo(40.dp)
    }

    /** A 16-pixel ornament stretched to the measure is a full-width picture of nothing. */
    @Test
    fun anOrnamentStaysAnOrnament() = runComposeUiTest {
        showFigure(width = 16, height = 16)

        onNodeWithContentDescription("plate").assertWidthIsEqualTo(16.dp)
    }

    @Test
    fun anIllustrationLargerThanTheMeasureIsShrunkToFitIt() = runComposeUiTest {
        showFigure(width = 2000, height = 1000)

        onNodeWithContentDescription("plate").assertWidthIsEqualTo(column)
    }

    /**
     * A plate taller than the page it was put on is held to the page rather than
     * running off the bottom of it - and is still never widened past its own
     * size to make up for it.
     */
    @Test
    fun anIllustrationTallerThanItsPageIsHeldToThePage() = runComposeUiTest {
        showFigure(width = 400, height = 1200, maxHeight = 300.dp)

        onNodeWithContentDescription("plate").assertHeightIsEqualTo(300.dp)
        onNodeWithContentDescription("plate").assertWidthIsEqualTo(400.dp)
    }

    /**
     * The setting is against the illustration's own size, so a book of diagrams
     * saved small can be made readable without every ornament in it filling the
     * measure.
     */
    @Test
    fun theReaderCanAskForIllustrationsLargerThanTheyAre() = runComposeUiTest {
        showFigure(width = 40, height = 20, size = FigureSize.Large)

        onNodeWithContentDescription("plate").assertWidthIsEqualTo(60.dp)
    }

    @Test
    fun theReaderCanAskForThemSmaller() = runComposeUiTest {
        showFigure(width = 40, height = 20, size = FigureSize.Small)

        onNodeWithContentDescription("plate").assertWidthIsEqualTo(20.dp)
    }

    /** Filling the measure is not a scale: the measure is all that stops it. */
    @Test
    fun fillDrawsEvenATinyOrnamentToTheMeasure() = runComposeUiTest {
        showFigure(width = 16, height = 16, size = FigureSize.Fill)

        onNodeWithContentDescription("plate").assertWidthIsEqualTo(column)
    }

    /** Asking for larger still cannot push an illustration past the measure. */
    @Test
    fun theMeasureIsStillTheLimitAtEverySetting() = runComposeUiTest {
        showFigure(width = 500, height = 250, size = FigureSize.Large)

        onNodeWithContentDescription("plate").assertWidthIsEqualTo(column)
    }
}
