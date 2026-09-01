package io.lukia.shared.presentation.components.controls

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import io.lukia.shared.presentation.theme.LukiaTheme
import io.lukia.shared.presentation.theme.MinTouchTarget
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * That the same list of choices fits a panel as well as a screen.
 *
 * Seventeen languages at the settings screen's row height are taller than the
 * reader's panel twice over, which is the whole reason [ChoiceDensity.Compact]
 * exists - and shrinking a row is only a good idea for as long as a thumb can
 * still land on it.
 */
@OptIn(ExperimentalTestApi::class)
class ChoiceListDensityTest {

    private val roomy = "Roomy"
    private val tight = "Tight"

    @Test
    fun aCompactRowIsShorterThanAComfortableOne() = runComposeUiTest {
        setContent {
            LukiaTheme(darkTheme = false, touch = false) {
                Column(modifier = Modifier.width(320.dp)) {
                    ChoiceList(
                        options = listOf(roomy),
                        selected = roomy,
                        label = { it },
                        onSelect = {},
                        detail = { "and what its name does not say" },
                    )

                    ChoiceList(
                        options = listOf(tight),
                        selected = tight,
                        label = { it },
                        onSelect = {},
                        detail = { "and what its name does not say" },
                        density = ChoiceDensity.Compact,
                    )
                }
            }
        }

        val comfortable = onNodeWithText(roomy, substring = true)
            .getUnclippedBoundsInRoot()
            .height

        val compact = onNodeWithText(tight, substring = true)
            .getUnclippedBoundsInRoot()
            .height

        assertTrue(
            compact < comfortable,
            "A compact row should be shorter than a comfortable one, was $compact against $comfortable",
        )
    }

    /**
     * The detail comes up beside the label rather than under it, which is most of
     * where the height goes - so a compact row is one line whether or not the
     * choice carries a second thing to say.
     */
    @Test
    fun aCompactRowIsTheSameHeightWithOrWithoutItsDetail() = runComposeUiTest {
        setContent {
            LukiaTheme(darkTheme = false, touch = false) {
                Column(modifier = Modifier.width(320.dp)) {
                    ChoiceList(
                        options = listOf(roomy),
                        selected = roomy,
                        label = { it },
                        onSelect = {},
                        density = ChoiceDensity.Compact,
                    )

                    ChoiceList(
                        options = listOf(tight),
                        selected = tight,
                        label = { it },
                        onSelect = {},
                        detail = { "and what its name does not say" },
                        density = ChoiceDensity.Compact,
                    )
                }
            }
        }

        val bare = onNodeWithText(roomy, substring = true).getUnclippedBoundsInRoot().height
        val detailed = onNodeWithText(tight, substring = true).getUnclippedBoundsInRoot().height

        assertTrue(
            bare == detailed,
            "A detail should cost a compact row no height, was $detailed against $bare",
        )
    }

    @Test
    fun aCompactRowIsStillBigEnoughForAThumb() = runComposeUiTest {
        setContent {
            LukiaTheme(darkTheme = false, touch = true) {
                Column(modifier = Modifier.width(320.dp)) {
                    ChoiceList(
                        options = listOf(tight),
                        selected = tight,
                        label = { it },
                        onSelect = {},
                        density = ChoiceDensity.Compact,
                    )
                }
            }
        }

        onNodeWithText(tight, substring = true).assertHeightIsAtLeast(MinTouchTarget)
    }
}
