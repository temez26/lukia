package io.lukia.shared.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.shared.presentation.theme.LukiaMotion
import io.lukia.shared.presentation.theme.MinTouchTarget

/** Inset of the selection pill from the track around it. */
private val TrackPadding: Dp = 3.dp

private const val DisabledAlpha = 0.38f

/**
 * One choice from a short list, in the style of an iOS segmented control.
 *
 * Segments share the width evenly so the track stays the same size whatever the
 * labels are, and each carries radio-button semantics so assistive technology
 * reads them as a single choice rather than as unrelated buttons.
 *
 * The selection is a single pill that slides between segments rather than a
 * background that switches on and off. That is what the control does on iOS, and
 * the movement is also what makes the change legible - a pill appearing somewhere
 * else is a cut, while one travelling there shows which choice was left behind.
 *
 * [selected] is nullable because a caller may be in a state that matches no
 * segment - the reader's page width when a pinch has left it between two presets.
 * The pill is hidden rather than guessed at, which is the honest answer.
 */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = MinTouchTarget
) {
    if (options.isEmpty()) return

    val selectedIndex = options.indexOf(selected)

    // Held at the last real selection while the pill fades out, so losing the
    // selection does not also send the pill sliding back to the first segment.
    val animatedIndex by animateFloatAsState(
        targetValue = selectedIndex.coerceAtLeast(0).toFloat(),
        animationSpec = LukiaMotion.gentle(),
        label = "segmentedSelection"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 1f else 0.5f)
            )
            .height(height)
            .padding(TrackPadding)
    ) {
        val segmentWidth = maxWidth / options.size

        if (selectedIndex >= 0) {
            Box(
                modifier = Modifier
                    .offset(x = segmentWidth * animatedIndex)
                    .width(segmentWidth)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
            )
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            options.forEach { option ->
                val isSelected = option == selected

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .width(segmentWidth)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .selectable(
                            selected = isSelected,
                            enabled = enabled,
                            role = Role.RadioButton,
                            onClick = { onSelect(option) }
                        )
                        .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
                ) {
                    Text(
                        text = label(option),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface.copy(
                            alpha = if (enabled) 1f else DisabledAlpha
                        )
                    )
                }
            }
        }
    }
}
