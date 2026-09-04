package io.lukia.features.library.presentation.components.selection

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.shared.presentation.components.icons.CheckGlyph
import io.lukia.shared.presentation.theme.LocalMotionStyle
import io.lukia.shared.presentation.theme.LukiaAlpha

/**
 * How one card takes part in selection, or null while the shelf is not being
 * selected from.
 *
 * One value rather than three parameters, because a card that is selectable
 * always knows all three things at once and a card that is not knows none of
 * them - which is exactly what a nullable value says and three booleans do not.
 */
@Stable
internal class CardSelection(
    val selected: Boolean,
    val onToggle: () -> Unit,
)

/** The tick on a selected cover, and the empty ring on one waiting to be. */
@Composable
internal fun SelectionMark(
    selected: Boolean,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            // A ring on a cover that may be any colour at all, so the unticked
            // state is drawn on its own ground rather than straight onto the
            // artwork - a hairline circle over a busy cover is a circle nobody
            // finds.
            .background(
                if (selected) {
                    colors.primary
                } else {
                    colors.surface.copy(alpha = LukiaAlpha.Fill)
                },
            )
            .border(
                width = 1.5.dp,
                color = if (selected) colors.primary else colors.onSurface.copy(alpha = 0.5f),
                shape = CircleShape,
            ),
    ) {
        if (selected) {
            CheckGlyph(
                tint = colors.onPrimary,
                modifier = Modifier.size(size * 0.6f).padding(1.dp),
            )
        }
    }
}

/**
 * The small rocking that says a shelf can be rearranged rather than read - the
 * one iPad has used for deleting apps since the beginning, and which readers
 * recognise before they have read a word of the bar above it.
 *
 * Every card is given its own timing from [seed], because a shelf rocking in
 * perfect unison reads as the whole page moving rather than as each book being
 * loose. The angle is deliberately under a degree and a half: enough to be
 * unmistakable at a glance, little enough that the titles stay readable while
 * somebody picks their way along a row.
 *
 * Zero when the shelf is not being selected from, and the animation does not
 * exist at all then - a library of a hundred cards each running a forever
 * animation is a hundred recompositions a frame for a mode nobody is in.
 *
 * Zero too where the app has been asked to move less. This is the app's one
 * animation with no end: a hundred covers rocking for as long as somebody takes
 * to pick their way along a shelf, which is exactly the shape of motion a
 * vestibular disorder cannot sit through and exactly what a device set to reduce
 * motion is asking not to see. The bar above still says the shelf is being
 * picked from, and every cover still wears its own tick.
 *
 * Handed back as state to be read where the card is drawn, never unwrapped
 * here - the same arrangement as `passageSweep`, and for the same reason. A
 * function that returns a value cannot restart on its own, so reading the
 * animation here would put the read in the caller's scope and recompose the
 * whole card sixty times a second: which is the cost above, paid by the mode
 * somebody *is* in rather than the one they are not.
 */
@Composable
internal fun jiggleAngle(active: Boolean, seed: Int): State<Float> {
    if (!active || !LocalMotionStyle.current.flourishes) {
        return remember { mutableFloatStateOf(0f) }
    }

    val transition = rememberInfiniteTransition(label = "jiggle")

    return transition.animateFloat(
        initialValue = -JiggleDegrees,
        targetValue = JiggleDegrees,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = JiggleMillis + (seed % JiggleSpread) * JiggleStep,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Reverse,
            // Started partway in, so neighbours are never at the same point of
            // the same swing.
            initialStartOffset = StartOffset((seed % JiggleSpread) * JiggleStep),
        ),
        label = "jiggleAngle",
    )
}

private const val JiggleDegrees = 1.1f
private const val JiggleMillis = 130
private const val JiggleSpread = 5
private const val JiggleStep = 17
