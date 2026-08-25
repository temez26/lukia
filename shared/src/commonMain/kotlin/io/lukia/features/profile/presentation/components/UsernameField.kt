package io.lukia.features.profile.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.features.profile.domain.UsernameProblem
import io.lukia.features.profile.domain.Usernames
import io.lukia.shared.presentation.components.focusRing
import io.lukia.shared.presentation.theme.LukiaAlpha
import io.lukia.shared.presentation.theme.Space

private val FieldPadding: Dp = 12.dp

/**
 * The one thing the app ever asks a reader to type about themselves.
 *
 * A name, capitalised as a name, with the keyboard's Done key ending the entry
 * rather than a button beside the field - both onboarding and the profile screen
 * carry their own way onward, and a field with a tick in it would be a third.
 *
 * What is typed is cleaned as it is typed - see [Usernames.clean] - so the
 * length limit is felt as the field stopping rather than as a rejection after
 * the fact, and what is shown here is exactly what will be stored.
 *
 * [problem] is shown only when the caller says so. A field that turns red on the
 * first character, before anybody has finished typing a two-letter name, is
 * scolding rather than helping.
 */
@Composable
internal fun UsernameField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    problem: UsernameProblem? = null,
    onSubmit: () -> Unit = {},
) {
    val interactionSource = remember { MutableInteractionSource() }
    val colors = MaterialTheme.colorScheme

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .background(colors.surfaceVariant.copy(alpha = LukiaAlpha.Fill))
                .focusRing(interactionSource, MaterialTheme.shapes.small)
                .padding(horizontal = FieldPadding, vertical = Space.lg),
        ) {
            if (value.isEmpty()) {
                Text(
                    text = "Your name",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            BasicTextField(
                value = value,
                onValueChange = { onValueChange(Usernames.clean(it)) },
                singleLine = true,
                interactionSource = interactionSource,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(
                    // A name, so the keyboard offers a capital first - the
                    // opposite of every other field in this app, all of which
                    // hold addresses and keys.
                    capitalization = KeyboardCapitalization.Words,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                // The placeholder is drawn beside the field rather than inside
                // it, so without this the field has no name to be announced by -
                // the same reason the library's search field carries one.
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Your name" },
            )
        }

        problem?.let {
            Text(
                text = it.message(),
                style = MaterialTheme.typography.bodySmall,
                color = colors.error,
                modifier = Modifier.padding(start = Space.xs, top = Space.sm),
            )
        }
    }
}

/**
 * What a rejected name is told to its owner. Here rather than in the domain,
 * which models what is wrong and leaves the wording to whoever is showing it -
 * the same split `AppErrorDisplay` makes.
 */
private fun UsernameProblem.message(): String = when (this) {
    UsernameProblem.Empty -> "Pick a name so the app knows what to call you."
    UsernameProblem.TooShort -> "A little longer - at least ${Usernames.MinLength} letters."
}
