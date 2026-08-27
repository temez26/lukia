package io.lukia.shared.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.shared.presentation.theme.LocalMotionStyle
import io.lukia.shared.presentation.theme.LukiaMotion
import kotlinx.coroutines.delay

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

/**
 * How long the reveal waits before its clock starts.
 *
 * It starts under the launch animation rather than after it - see
 * [LaunchTiming.ContentArrivalMillis]. Waiting for the mark to be gone first
 * would leave an empty screen behind it and then assemble the library in front
 * of the reader, which is the one moment in the app where that is most obvious.
 * Starting early instead means the mark dissolves off a screen whose top has
 * already settled, with only the lower rows still arriving.
 */
private const val StartDelayMillis = LaunchTiming.ContentArrivalMillis

/** How far an element rises into place. */
private val DefaultRise: Dp = 14.dp

/**
 * The app's opening, as a moment in the life of the process rather than
 * something any one screen owns.
 *
 * Process-scoped on purpose: this is about the app opening, not about a screen
 * being composed, so coming back to the library from a book must not replay it,
 * and an Android configuration change - which recreates the activity without the
 * process ever having stopped - is not a launch either.
 *
 * One clock, so that a screen composing while it runs joins the stagger part-way
 * through rather than starting one of its own. That is the whole point of it
 * being shared: an element's turn comes at a fixed point after launch, not at a
 * fixed point after whenever it happened to be composed.
 */
private object AppOpening {

    private var claimed = false

    /** How far into the reveal the app is, in milliseconds, for every screen taking part. */
    val elapsed = Animatable(0f)

    /** Whether the opening is still going on, as a screen composing now finds it. */
    var running = false
        private set

    fun claim(): Boolean {
        if (claimed) return false

        claimed = true
        running = true
        return true
    }

    fun finish() {
        running = false
    }
}

/**
 * Claims the app's opening for this run of the process and starts the reveal
 * underneath it, answering true for the first caller only.
 *
 * Called from [LaunchSplash] and nowhere else, because the mark and the reveal
 * are two halves of one opening and a second gate could only disagree with the
 * first about when the app opened.
 */
@Composable
internal fun rememberAppOpening(): Boolean {
    val opening = remember { AppOpening.claim() }
    val motion = LocalMotionStyle.current

    LaunchedEffect(opening) {
        if (!opening) return@LaunchedEffect

        try {
            // Stretched by the same factor as the launch animation above it, so
            // the two still hand over to each other at the moment they were
            // written to - see [LaunchTiming].
            delay((StartDelayMillis * motion.stretch).toLong())

            // Linear: the shaping belongs to each element's own curve, applied
            // in [launchReveal]. Easing the shared clock as well would stagger
            // the stagger.
            AppOpening.elapsed.animateTo(
                targetValue = TotalMillis.toFloat(),
                animationSpec = tween(
                    durationMillis = (TotalMillis * motion.stretch).toInt(),
                    easing = LinearEasing,
                ),
            )
        } finally {
            // On the way out as well as at the end: an activity torn down
            // part-way through has ended the opening, and a window left open
            // behind it would hand the next screen composed a reveal to play
            // off a clock that has stopped.
            AppOpening.finish()
        }
    }

    return opening
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
    /**
     * Whether elements take their turns one after another or all arrive at
     * once. A stagger is something to watch, which is exactly what the calmest
     * of the motion presets is asking the app not to be.
     */
    private val staggered: Boolean,
    /** How far an element rises, against the standard distance. */
    internal val travel: Float,
) {
    /**
     * How far into its arrival the element at [index] is, from 0 to 1. Always 1
     * when the reveal is not playing, so callers need no branch of their own.
     */
    fun progressFor(index: Int): Float {
        if (!playing) return 1f

        val turn = if (staggered) index.coerceIn(0, MaxStaggered) else 0
        return ((elapsedMillis.value - StaggerMillis * turn) / ElementMillis).coerceIn(0f, 1f)
    }

    companion object {

        /** A reveal with nothing left to reveal, which is what most of the app's life is. */
        internal val Finished = LaunchReveal(
            elapsedMillis = mutableFloatStateOf(0f),
            playing = false,
            staggered = false,
            travel = 1f,
        )
    }
}

/**
 * This screen's share of the app's opening, or a reveal already over when the
 * app opened before this screen existed.
 *
 * Decided once, as the screen composes, and never revisited: whether a screen
 * was there as the app opened is a fact about that screen, and a reveal that
 * could start later is a screen change wearing the launch animation's clothes.
 * It used to be claimed by whichever screen asked first, which is how the
 * library came to play the app's opening - once, minutes in - as the animation
 * for switching to its tab.
 */
@Composable
fun rememberLaunchReveal(): LaunchReveal {
    val playing = remember { AppOpening.running }
    val motion = LocalMotionStyle.current

    return remember(playing, motion) {
        if (!playing) {
            LaunchReveal.Finished
        } else {
            LaunchReveal(
                elapsedMillis = AppOpening.elapsed.asState(),
                playing = true,
                staggered = motion.flourishes,
                travel = motion.travel,
            )
        }
    }
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
    translationY = rise.toPx() * reveal.travel * (1f - progress)
}
