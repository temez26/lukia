package io.lukia.features.library.presentation.components.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import io.lukia.resources.Res
import io.lukia.resources.search_clear
import io.lukia.resources.search_your_library
import io.lukia.shared.presentation.components.controls.LukiaIconButton
import io.lukia.shared.presentation.components.controls.focusRing
import io.lukia.shared.presentation.components.icons.ClearGlyph
import io.lukia.shared.presentation.components.icons.SearchGlyph
import io.lukia.shared.presentation.theme.LocalLukiaMetrics
import io.lukia.shared.presentation.theme.LukiaAlpha
import io.lukia.shared.presentation.theme.LukiaMotion
import io.lukia.shared.presentation.theme.Space
import io.lukia.shared.presentation.theme.isWideWindow
import org.jetbrains.compose.resources.stringResource

/**
 * How tall the field is in this window. Read by the bar around it, which is
 * sized to hold it.
 *
 * A thumb target on a phone; on anything wider it sits in a toolbar row beside
 * the actions button and takes the same height every other control there does.
 */
@Composable
internal fun searchFieldHeight(): Dp {
    val metrics = LocalLukiaMetrics.current
    return if (isWideWindow) metrics.controlHeight else metrics.controlHeightSmall
}

/**
 * The library's search field.
 *
 * Built from [BasicTextField] rather than Material's `TextField`: the rest of
 * this app's chrome is drawn by hand to Apple's proportions, and Material's field
 * brings a label, an indicator line and its own container along with it.
 *
 * The field takes a focus ring rather than changing shape or size when it gains
 * focus, so nothing below it moves as the keyboard opens.
 *
 * It sizes itself to the window it is in. A phone's field is a thumb target
 * carrying 17pt text; a desktop's sits in a toolbar row beside the filter, and
 * wearing phone proportions there is most of what makes a desktop window look
 * like a scaled-up phone.
 */
@Composable
internal fun LibrarySearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    /**
     * The reader saying they have finished typing, by pressing the keyboard's
     * search key. Nothing to run - the list is already filtered - but it is the
     * one moment a half-typed word can be told from a search somebody made.
     */
    onSubmit: () -> Unit = {},
) {
    val compact = !isWideWindow
    val fieldHeight = searchFieldHeight()
    val textStyle = if (compact) {
        MaterialTheme.typography.bodyLarge
    } else {
        MaterialTheme.typography.bodyMedium
    }

    val focusManager = LocalFocusManager.current
    val interactionSource = remember { MutableInteractionSource() }
    val shape = MaterialTheme.shapes.small

    // Read before the modifier chain: `semantics` is not a composable scope.
    val spoken = stringResource(Res.string.search_your_library)

    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = LocalTextStyle.current.merge(
            textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        // Nothing to run - the list is already filtered as the query is typed -
        // so the key only reports that the search was made and puts the keyboard
        // away.
        keyboardActions = KeyboardActions(
            onSearch = {
                onSubmit()
                focusManager.clearFocus()
            },
        ),
        interactionSource = interactionSource,
        modifier = modifier
            .height(fieldHeight)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = LukiaAlpha.Fill))
            // The same ring every other control in the app wears when the
            // keyboard is on it - this field used to draw a half-strength one of
            // its own, so tabbing from the filter into the search looked like
            // losing focus rather than moving it.
            .focusRing(interactionSource, shape)
            .pointerHoverIcon(PointerIcon.Text)
            .semantics { contentDescription = spoken },
        decorationBox = { textField ->
            SearchFieldDecoration(
                query = query,
                placeholder = placeholder,
                textStyle = textStyle,
                fieldHeight = fieldHeight,
                onClear = {
                    onQueryChange("")
                    focusManager.clearFocus()
                },
                textField = textField,
            )
        },
    )
}

/**
 * What sits in the field around the text being typed: the magnifier, the
 * placeholder while it is empty, and the way to empty it again.
 *
 * Apart from the field itself because none of it is the field - it is chrome
 * laid over one, and `BasicTextField` takes it as a slot precisely so the two
 * can be written apart.
 */
@Composable
private fun SearchFieldDecoration(
    query: String,
    placeholder: String,
    textStyle: TextStyle,
    fieldHeight: Dp,
    onClear: () -> Unit,
    textField: @Composable () -> Unit,
) {
    val metrics = LocalLukiaMetrics.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = Space.md),
    ) {
        SearchGlyph(
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(metrics.iconSmall),
        )

        Spacer(modifier = Modifier.width(Space.sm))

        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = placeholder,
                    style = textStyle,
                    // One line: the field is short on a phone, and a placeholder
                    // that wraps or clips mid-count reads as a bug rather than
                    // as a hint.
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            textField()
        }

        ClearButton(
            visible = query.isNotEmpty(),
            size = fieldHeight,
            glyphSize = metrics.iconSmall,
            // The cross is cut out of the circle in the colour of the field
            // behind it, so it reads in both themes.
            knockout = MaterialTheme.colorScheme.surfaceVariant,
            onClick = onClear,
        )
    }
}

@Composable
private fun ClearButton(
    visible: Boolean,
    size: Dp,
    glyphSize: Dp,
    knockout: Color,
    onClick: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(LukiaMotion.quick()) +
            scaleIn(LukiaMotion.quick(), initialScale = 0.7f),
        exit = fadeOut(LukiaMotion.quick()) +
            scaleOut(LukiaMotion.quick(), targetScale = 0.7f),
    ) {
        LukiaIconButton(
            label = stringResource(Res.string.search_clear),
            onClick = onClick,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            size = size,
            glyphSize = glyphSize,
        ) { tint, glyph ->
            ClearGlyph(tint = tint, knockout = knockout, modifier = glyph)
        }
    }
}
