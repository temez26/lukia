package io.lukia.shared.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.shared.presentation.theme.LukiaMotion

/** Gap between one element's reveal and the next one's. */
private const val StaggerMillis = 45

/**
 * How many elements are staggered before the rest come in together. Without a cap
 * the last row of a large library would wait several seconds for its turn.
 */
private const val MaxStaggered = 11

/** How long one element takes to arrive, once its turn comes. */
private const val ElementMillis = 380

private const val TotalMillis = StaggerMillis * MaxStaggered + ElementMillis

/** How far an element rises into place. */
private val DefaultRise: Dp = 14.dp

/**
 * Claims the app-open reveal for the first composition that asks for it.
 *
 * Process-scoped rather than screen-scoped on purpose: this animation is about
 * the app opening, not about the library being composed, so coming back to the
 * library from a book must not replay it. Surviving an Android configuration
 * change is the same property seen from the other side - a rotation is not a
 * launch either.
 */
private object LaunchRevealGate {
    private var claimed = false

    fun claim(): Boolean {
        if (claimed) return false
        claimed = true
        return true
    }
}

/**
 * The app-open reveal: content fades up in a short stagger the first time the app
 * is drawn, in the manner of an iOS app coming to the foreground.
 *
 * One clock drives every element rather than each running its own delayed
 * animation. That is what keeps the stagger honest - an element's turn comes at a
 * fixed point after launch, not at a fixed point after whenever it happened to be
 * composed - and it means a library scrolled during the reveal does not hand its
 * later rows an animation that launch has long since finished.
 */
@Stable
class LaunchReveal internal constructor(
    private val elapsedMillis: State<Float>,
    private val playing: Boolean,
) {
    /**
     * How far into its arrival the element at [index] is, from 0 to 1. Always 1
     * when the reveal is not playing, so callers need no branch of their own.
     */
    fun progressFor(index: Int): Float {
        if (!playing) return 1f

        val start = StaggerMillis * index.coerceIn(0, MaxStaggered)
        return ((elapsedMillis.value - start) / ElementMillis).coerceIn(0f, 1f)
    }
}

@Composable
fun rememberLaunchReveal(): LaunchReveal {
    val playing = remember { LaunchRevealGate.claim() }
    val elapsed = remember { Animatable(0f) }

    LaunchedEffect(playing) {
        if (playing) {
            // Linear: the shaping belongs to each element's own curve, applied in
            // [launchReveal]. Easing the shared clock as well would stagger the
            // stagger.
            elapsed.animateTo(
                targetValue = TotalMillis.toFloat(),
                animationSpec = tween(durationMillis = TotalMillis, easing = LinearEasing),
            )
        }
    }

    return remember(playing) { LaunchReveal(elapsed.asState(), playing) }
}

/**
 * Fades and lifts this element into place as its turn in [reveal] comes.
 *
 * The progress is read inside the `graphicsLayer` block, so each frame re-draws
 * the layer without recomposing anything - which matters when the elements being
 * revealed are a grid of book covers.
 */
fun Modifier.launchReveal(
    reveal: LaunchReveal,
    index: Int,
    rise: Dp = DefaultRise,
): Modifier = graphicsLayer {
    val progress = LukiaMotion.Standard.transform(reveal.progressFor(index))
    alpha = progress
    translationY = rise.toPx() * (1f - progress)
}
