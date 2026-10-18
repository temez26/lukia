package io.lukia.shared.presentation.components.surface

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.resources.Res
import io.lukia.resources.action_close_panel
import io.lukia.shared.presentation.components.controls.pressable
import io.lukia.shared.presentation.components.menu.DismissOnBack
import io.lukia.shared.presentation.components.menu.popoverTransition
import io.lukia.shared.presentation.components.menu.rememberPopoverProgress
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

private val SheetTransformOrigin = TransformOrigin(0.5f, 0f)

private val HandleWidth: Dp = 36.dp
private val HandleHeight: Dp = 4.dp

/** The strip the handle is pressed and dragged by: the whole width, above the 24dp floor. */
private val HandleAreaHeight: Dp = 28.dp

/** How far up, as a share of its height, a let-go sheet has to be to close. */
private const val DismissFraction = 0.25f

/**
 * A panel that drops from the top of the window, out of the bar above the page,
 * and can be pushed back up into it.
 *
 * Built to be one block with that bar: [topClearance] is left empty at its top
 * for the bar to sit in, drawn over the sheet by whoever owns the bar, so the
 * bar, what it opened and everything under it read as one surface rather than
 * a capsule with a second panel hung beneath it.
 *
 * Grows to its content and no further than the room it is given; give [content]
 * a `weight(1f, fill = false)` list to let it scroll inside that room. The handle
 * at its foot closes it when dragged up far enough, and when pressed, which is
 * what a screen reader or a keyboard can do with it. Laid out in the window
 * rather than as a popup, so a keyboard and a switch control can reach into it.
 *
 * Draws nothing once closed and settled, so callers can leave it in the tree.
 */
@Composable
fun TopSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    topClearance: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val progress = rememberPopoverProgress(visible, label = "topSheet")

    if (!visible && progress == 0f) return

    DismissOnBack(enabled = visible, onDismiss = onDismiss)

    var height by remember { mutableIntStateOf(0) }
    val drag = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    // Opening again starts from where it rests, not where the last drag left it.
    LaunchedEffect(visible) { if (visible) drag.snapTo(0f) }

    val shape = MaterialTheme.shapes.extraLarge

    GlassSurface(
        shape = shape,
        tone = GlassTone.Panel,
        modifier = modifier
            .onSizeChanged { height = it.height }
            // Out of the bar at its top, as every panel grows from what opened it.
            .popoverTransition(progress, SheetTransformOrigin)
            .graphicsLayer { translationY = drag.value }
            // Solid under the glass: a sheet tall enough to cover the page is
            // one whose rows are read over the page's own lines otherwise.
            .background(MaterialTheme.colorScheme.surface, shape),
    ) {
        Column(modifier = Modifier.padding(top = topClearance)) {
            content()

            DragHandle(
                onDrag = { delta -> scope.launch { drag.snapTo((drag.value + delta).coerceAtMost(0f)) } },
                onRelease = {
                    scope.launch {
                        if (-drag.value > height * DismissFraction) onDismiss() else drag.animateTo(0f)
                    }
                },
                onClick = onDismiss,
            )
        }
    }
}

@Composable
private fun DragHandle(
    onDrag: (Float) -> Unit,
    onRelease: () -> Unit,
    onClick: () -> Unit,
) {
    val label = stringResource(Res.string.action_close_panel)
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(HandleAreaHeight)
            .draggable(
                state = rememberDraggableState(onDrag),
                orientation = Orientation.Vertical,
                onDragStopped = { onRelease() },
            )
            .pressable(
                interactionSource = interactionSource,
                shape = MaterialTheme.shapes.small,
                onClickLabel = label,
                onClick = onClick,
            )
            .semantics { contentDescription = label },
    ) {
        Box(
            modifier = Modifier
                .size(width = HandleWidth, height = HandleHeight)
                .background(MaterialTheme.colorScheme.outlineVariant, CircleShape),
        )
    }
}
