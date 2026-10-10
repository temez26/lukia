package io.lukia.shared.presentation.components.controls

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.shared.presentation.theme.LocalLukiaMetrics
import io.lukia.shared.presentation.theme.LukiaAlpha
import io.lukia.shared.presentation.theme.LukiaMotion

/** Inset of the selection pill from the track around it. */
private val TrackPadding: Dp = 2.dp

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
    /**
     * Composable, because what a choice is called is a string resource and a
     * resource is read from a composition. See the display mappers beside each
     * feature's own choices.
     */
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = Dp.Unspecified,
) {
    if (options.isEmpty()) return

    val metrics = LocalLukiaMetrics.current
    val trackHeight = if (height == Dp.Unspecified) metrics.controlHeight else height
    val selectedIndex = options.indexOf(selected)

    // A rounded rect rather than a capsule: that is the shape iOS actually gives
    // this control, and a full capsule on a desktop window reads as a pill of
    // chrome floating in the layout rather than as a control in it.
    val trackShape = MaterialTheme.shapes.small
    val pillShape = MaterialTheme.shapes.extraSmall

    // Held at the last real selection while the pill fades out, so losing the
    // selection does not also send the pill sliding back to the first segment.
    val animatedIndex by animateFloatAsState(
        targetValue = selectedIndex.coerceAtLeast(0).toFloat(),
        animationSpec = LukiaMotion.gentle(),
        label = "segmentedSelection",
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(trackShape)
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 1f else 0.5f),
            )
            // Fixed rather than a floor, unlike every other control here. The
            // pill and segments all fill this height, and `fillMaxHeight` inside
            // a container that wraps its content resolves against whatever the
            // parent offered - for a track in a settings column, the whole
            // window. A floor here is a control the height of the screen.
            //
            // What that costs is headroom: a segment's `labelSmall` is a 32dp
            // line at the largest OS font scale, and this track is 44dp with
            // 2dp of inset either side. It fits, with 6dp to spare, and past
            // that the label is clipped rather than the layout broken.
            .height(trackHeight)
            .padding(TrackPadding),
    ) {
        val segmentWidth = maxWidth / options.size

        if (selectedIndex >= 0) {
            Box(
                modifier = Modifier
                    .offset(x = segmentWidth * animatedIndex)
                    .width(segmentWidth)
                    .fillMaxHeight()
                    .clip(pillShape)
                    .background(MaterialTheme.colorScheme.surface),
            )
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            options.forEach { option ->
                Segment(
                    text = label(option),
                    width = segmentWidth,
                    shape = pillShape,
                    selected = option == selected,
                    enabled = enabled,
                    onClick = { onSelect(option) },
                )
            }
        }
    }
}

/**
 * One segment: its word, its target and its hover and focus treatment. The pill
 * behind the chosen one is drawn by [SegmentedControl] rather than here, because
 * it travels between segments and belongs to none of them.
 */
@Composable
private fun Segment(
    text: String,
    width: Dp,
    shape: Shape,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .clip(shape)
            .pressFeedback(
                interactionSource = interactionSource,
                shape = shape,
                enabled = enabled,
            )
            .selectable(
                selected = selected,
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface.copy(
                alpha = if (enabled) 1f else LukiaAlpha.Disabled,
            ),
        )
    }
}
