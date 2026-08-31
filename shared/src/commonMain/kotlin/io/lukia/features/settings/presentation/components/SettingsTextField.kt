package io.lukia.features.settings.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.shared.presentation.components.controls.IconButtonVariant
import io.lukia.shared.presentation.components.controls.LukiaIconButton
import io.lukia.shared.presentation.components.controls.focusRing
import io.lukia.shared.presentation.components.icons.ClearGlyph
import io.lukia.shared.presentation.theme.LukiaAlpha
import io.lukia.shared.presentation.theme.Space

private val FieldPadding: Dp = 12.dp

/**
 * A single line of settings the reader types or pastes into.
 *
 * [secret] masks what is typed and offers to reveal it. That is for the API
 * key: a settings screen is opened in places where somebody is looking over
 * your shoulder, and it is the one value here worth reading off a screen - but
 * a key that can never be checked is one nobody can tell has a character
 * missing. An address or a model name is neither secret nor memorable, and is
 * shown plainly.
 *
 * The value is reported as it is typed rather than on a save button. Storing
 * per keystroke is cheap - these are short strings in the same store as every
 * other preference - and it means a key pasted in works on the very next tap in
 * the reader.
 */
@Composable
internal fun SettingsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    secret: Boolean = false,
) {
    var revealed by remember { mutableStateOf(false) }
    val masked = secret && !revealed
    val interactionSource = remember { MutableInteractionSource() }
    val colors = MaterialTheme.colorScheme

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(colors.surfaceVariant.copy(alpha = LukiaAlpha.Fill))
            .focusRing(interactionSource, MaterialTheme.shapes.small)
            .padding(horizontal = FieldPadding, vertical = 4.dp),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                interactionSource = interactionSource,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                visualTransformation = if (masked) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
                // No capitals whatever this holds: none of a key, a URL or a
                // model name is a word, and a keyboard that helpfully
                // capitalises the first character of one produces a value that
                // is silently rejected.
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (secret) KeyboardType.Password else KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth().padding(vertical = Space.sm),
            )
        }

        if (value.isNotEmpty()) {
            Spacer(modifier = Modifier.width(Space.sm))

            if (secret) RevealToggle(revealed = revealed, onToggle = { revealed = !revealed })

            LukiaIconButton(
                label = "Clear",
                onClick = { onValueChange("") },
                variant = IconButtonVariant.Plain,
            ) { tint, glyph ->
                ClearGlyph(
                    tint = tint,
                    knockout = MaterialTheme.colorScheme.surface,
                    modifier = glyph,
                )
            }
        }
    }
}

/**
 * Words rather than an eye glyph. There is no icon in this app's set for it, and
 * "Show" says exactly what it does in less space than drawing one would take.
 */
@Composable
private fun RevealToggle(revealed: Boolean, onToggle: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }

    Text(
        text = if (revealed) "Hide" else "Show",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onToggle,
            )
            .focusRing(interactionSource, MaterialTheme.shapes.extraSmall)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = Space.sm, vertical = Space.xs),
    )
}
