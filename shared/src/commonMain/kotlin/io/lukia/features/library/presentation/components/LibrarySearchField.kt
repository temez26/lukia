package io.lukia.features.library.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.shared.presentation.components.ClearGlyph
import io.lukia.shared.presentation.components.SearchGlyph
import io.lukia.shared.presentation.theme.LocalWindowWidthClass
import io.lukia.shared.presentation.theme.LukiaMotion
import io.lukia.shared.presentation.theme.WindowWidthClass

/** iOS's search field height, sized to be comfortably tappable with a thumb. */
private val TouchFieldHeight: Dp = 38.dp

/**
 * How tall the field is in this window. Read by the bar around it, which is
 * sized to hold it.
 */
@Composable
internal fun searchFieldHeight(): Dp =
    if (LocalWindowWidthClass.current == WindowWidthClass.Compact) {
        TouchFieldHeight
    } else {
        HeaderControlHeight
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
    modifier: Modifier = Modifier
) {
    val compact = LocalWindowWidthClass.current == WindowWidthClass.Compact
    val fieldHeight = searchFieldHeight()
    val textStyle = if (compact) {
        MaterialTheme.typography.bodyLarge
    } else {
        MaterialTheme.typography.bodyMedium
    }

    val focusManager = LocalFocusManager.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(if (compact) 11.dp else 8.dp)

    val borderColor by animateColorAsState(
        targetValue = if (isFocused) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0f)
        },
        animationSpec = LukiaMotion.quick(),
        label = "searchFieldBorder"
    )

    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = LocalTextStyle.current.merge(
            textStyle.copy(color = MaterialTheme.colorScheme.onSurface)
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        // Nothing to submit - the list is already filtered as the query is
        // typed - so the only thing left for the key to do is put the keyboard
        // away.
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        interactionSource = interactionSource,
        modifier = modifier
            .height(fieldHeight)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .border(width = 1.dp, color = borderColor, shape = shape)
            .pointerHoverIcon(PointerIcon.Text)
            .semantics { contentDescription = "Search your library" },
        decorationBox = { textField ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 9.dp)
            ) {
                SearchGlyph(
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )

                Spacer(modifier = Modifier.width(6.dp))

                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = textStyle,
                            // One line: the field is short on a phone, and a
                            // placeholder that wraps or clips mid-count reads as a
                            // bug rather than as a hint.
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    textField()
                }

                ClearButton(
                    visible = query.isNotEmpty(),
                    size = fieldHeight,
                    // The cross is cut out of the circle in the colour of the
                    // field behind it, so it reads in both themes.
                    knockout = MaterialTheme.colorScheme.surfaceVariant,
                    onClick = {
                        onQueryChange("")
                        focusManager.clearFocus()
                    }
                )
            }
        }
    )
}

@Composable
private fun ClearButton(
    visible: Boolean,
    size: Dp,
    knockout: Color,
    onClick: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(LukiaMotion.quick()) +
            scaleIn(LukiaMotion.quick(), initialScale = 0.7f),
        exit = fadeOut(LukiaMotion.quick()) +
            scaleOut(LukiaMotion.quick(), targetScale = 0.7f)
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(onClickLabel = "Clear search", onClick = onClick)
                .semantics { contentDescription = "Clear search" },
            contentAlignment = Alignment.Center
        ) {
            ClearGlyph(
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                knockout = knockout,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
