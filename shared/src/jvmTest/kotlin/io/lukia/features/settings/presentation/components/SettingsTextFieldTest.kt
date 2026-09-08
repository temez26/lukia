package io.lukia.features.settings.presentation.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.lukia.shared.presentation.theme.LukiaTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Where the placeholder sits, which is on the line the typed text will appear
 * on and not above it.
 *
 * The two share a box and are not the same height - the field carries vertical
 * padding of its own and the placeholder carries none - so aligned at the top,
 * "Public endpoint" rode high in a field taller than itself. Aligning them
 * centres the shorter one against the taller.
 */
@OptIn(ExperimentalTestApi::class)
class SettingsTextFieldTest {

    @Test
    fun thePlaceholderIsCentredInTheField() = runComposeUiTest {
        setContent {
            LukiaTheme(darkTheme = false) {
                SettingsTextField(
                    value = "",
                    onValueChange = {},
                    placeholder = Placeholder,
                )
            }
        }

        val placeholder = onNodeWithText(Placeholder).getBoundsInRoot()
        // The field itself rather than the window: it is the only node here that
        // knows how tall a line of typed text will be, padding included.
        val field = onNode(hasSetTextAction()).getBoundsInRoot()

        val placeholderCentre = (placeholder.top + placeholder.bottom) / 2
        val fieldCentre = (field.top + field.bottom) / 2

        assertTrue(
            abs((placeholderCentre - fieldCentre).value) <= Tolerance.value,
            "the placeholder sits at $placeholderCentre in a field centred on $fieldCentre",
        )
    }

    private companion object {
        /** The one the reader spotted, under Google in Looking up a word. */
        const val Placeholder = "Public endpoint"

        /** Rounding between a text baseline and a box, and nothing more. */
        val Tolerance = 1.dp
    }
}
