package io.lukia.shared.presentation.components.launch

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.shared.presentation.theme.LocalMotionStyle
import io.lukia.shared.presentation.theme.LukiaMotion
import io.lukia.shared.presentation.theme.MotionStyle
import io.lukia.shared.presentation.theme.isWideWindow

/**
 * The launch animation's clock, in milliseconds from the app's first frame.
 *
 * Gathered in one object because four separate things read from it - the mark,
 * the sparkles around it, the app's own arrival below and the stagger in
 * [LaunchReveal] - and the whole effect depends on them agreeing. A duration
 * edited in one of the four and not the others is a seam.
 *
 * The order of the moments matters more than any of the numbers:
 *
 * ```
 * 0        the mark fades in and springs into place
 * 120      the leaves fan open, back of the stack first
 * 480      the app below starts arriving, still hidden
 * 860      the mark begins to leave; the app grows out to full size
 * 1160     the mark is gone
 * 1260     the app has settled
 * ```
 */
internal object LaunchTiming {

    /** How long the mark takes to fade in. The scale keeps going after this. */
    const val ArrivalMillis = 280

    /**
     * When the leaves start swinging out of the stack.
     *
     * After the mark is most of the way visible rather than at zero: a fan that
     * opens while it is still fading up is a movement nobody quite sees, and
     * the point of it is to be seen.
     */
    const val FanStartMillis = 120

    /** How long the whole fan takes, its stagger included. */
    const val FanMillis = 440

    /**
     * When the app underneath starts arriving - before the mark leaves, and
     * that is the point.
     *
     * The library finishes loading somewhere in this stretch, and a spinner
     * giving way to a grid of covers is exactly the kind of change that should
     * happen behind something opaque rather than in front of the reader. By the
     * time the mark begins to dissolve the top of the screen has settled and
     * only the lower rows are still coming in, so what is uncovered is a screen
     * that was already there rather than one that starts assembling itself the
     * moment it is looked at.
     */
    const val ContentArrivalMillis = 480

    /** When the mark begins to leave. */
    const val HandoffMillis = 860

    /** The cross-fade into the app underneath. */
    const val DissolveMillis = 300

    /**
     * How long the app takes to grow out to full size. Runs past the dissolve
     * on purpose: an expansion that stops at the same instant the mark vanishes
     * makes both read as one abrupt event, where letting it settle afterwards
     * makes the mark's leaving and the app's arriving the same movement.
     */
    const val ExpandMillis = 400

    const val TotalMillis = HandoffMillis + ExpandMillis

    /**
     * Where the mark starts. A gentle rise rather than a zoom - it should read
     * as the icon coming into focus, not as it flying at the reader.
     */
    const val InitialScale = 0.88f

    /**
     * How much further the mark grows while dissolving, which is what makes the
     * handover read as the icon opening into the app rather than as a slide
     * being taken away.
     */
    const val ExitExpansion = 0.06f

    /**
     * How far under full size the app waits.
     *
     * Small enough that it is never seen as a shrunken screen - only as one
     * arriving. It grows while the mark grows, so the two are travelling the
     * same way at the moment one becomes the other; the app fading up instead
     * would have both at half opacity in the middle of the handover, which is
     * how a cross-fade turns into a wash.
     */
    const val ContentInitialScale = 0.96f

    fun arrival(elapsedMillis: Float): Float = (elapsedMillis / ArrivalMillis).coerceIn(0f, 1f)

    fun fan(elapsedMillis: Float): Float =
        ((elapsedMillis - FanStartMillis) / FanMillis).coerceIn(0f, 1f)

    fun dissolve(elapsedMillis: Float): Float =
        ((elapsedMillis - HandoffMillis) / DissolveMillis).coerceIn(0f, 1f)

    fun expansion(elapsedMillis: Float): Float =
        ((elapsedMillis - HandoffMillis) / ExpandMillis).coerceIn(0f, 1f)

    /** Where the app waits, once the reader's preset has had its say. */
    fun contentScaleFor(travel: Float): Float = 1f - (1f - ContentInitialScale) * travel
}

/** The mark's size, which is the one thing about it a larger window changes. */
private val CompactLogoSize: Dp = 104.dp
private val WideLogoSize: Dp = 128.dp

/** The wash of accent behind the mark, at the alpha of a suggestion. */
private const val GlowAlpha = 0.1f

/** How far past the mark that wash reaches. */
private const val GlowExtent = 2f

/**
 * The app's opening: the mark arriving over the app's own background, settling
 * with a single soft overshoot, and dissolving into whatever [content] is as
 * that grows out to meet it.
 *
 * [content] is composed underneath from the first frame rather than after the
 * animation, so the screen behind the mark is loaded, laid out and part-way
 * through its own arrival by the time the dissolve starts: the handover is a
 * cover being taken off a screen that was there all along, not a swap.
 *
 * One clock drives every part of it. Two would drift, in the one place the whole
 * effect is judged.
 */
@Composable
fun LaunchSplash(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    // The one place the app's opening is claimed, for the mark here and for the
    // reveal on the screens underneath both - see [rememberAppOpening]. Held
    // apart from [playing], which goes false the instant the mark is gone while
    // the reveal beneath it is still finishing.
    val opening = rememberAppOpening()
    var playing by remember { mutableStateOf(opening) }
    val elapsed = remember { Animatable(0f) }
    val clock = elapsed.asState()
    val motion = LocalMotionStyle.current

    LaunchedEffect(Unit) {
        if (!playing) return@LaunchedEffect

        // The reader's preset stretches the clock rather than any one duration,
        // so the timeline written above stays true whatever pace it runs at.
        //
        // Linear, because every curve in this animation is applied where it is
        // read.
        elapsed.animateTo(
            targetValue = LaunchTiming.TotalMillis.toFloat(),
            animationSpec = tween(
                durationMillis = (LaunchTiming.TotalMillis * motion.stretch).toInt(),
                easing = LinearEasing,
            ),
        )
        playing = false
    }

    Box(modifier = modifier.fillMaxSize()) {
        Box(modifier = if (playing) Modifier.launchExpand(clock, motion.travel) else Modifier) {
            content()
        }

        if (playing) {
            LaunchOverlay(clock, motion)
        }
    }
}

/**
 * Grows the app out to full size as the mark leaves it.
 *
 * The clock is read inside the `graphicsLayer` block, so a frame of this costs
 * one layer's transform rather than a recomposition of every screen underneath.
 */
private fun Modifier.launchExpand(
    elapsedMillis: State<Float>,
    travel: Float,
): Modifier = graphicsLayer {
    val from = LaunchTiming.contentScaleFor(travel)
    val grown = LukiaMotion.Standard.transform(LaunchTiming.expansion(elapsedMillis.value))

    scaleX = from + (1f - from) * grown
    scaleY = from + (1f - from) * grown
}

/**
 * The mark and everything around it, removed from the tree once it is over.
 *
 * The shared clock drives the fades and a spring drives the settle, which is the
 * division of labour the two need: the timings have to line up with each other,
 * while the settle has to be a settle rather than a curve pretending to be one.
 */
@Composable
private fun LaunchOverlay(elapsedMillis: State<Float>, motion: MotionStyle) {
    val settle = remember { Animatable(LaunchTiming.InitialScale) }

    LaunchedEffect(Unit) {
        // Overshoot is a flourish; arriving is not. Without it the mark still
        // grows into place, it just stops when it gets there.
        settle.animateTo(1f, if (motion.flourishes) LukiaMotion.bouncy() else LukiaMotion.gentle())
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = 1f - LukiaMotion.Standard.transform(
                    LaunchTiming.dissolve(elapsedMillis.value),
                )
            }
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        LaunchMark(
            elapsedMillis = elapsedMillis,
            settle = settle.asState(),
            logoSize = if (isWideWindow) WideLogoSize else CompactLogoSize,
            flourishes = motion.flourishes,
        )
    }
}

/** The mark, its glow and its sparkles, which arrive and leave as one thing. */
@Composable
private fun LaunchMark(
    elapsedMillis: State<Float>,
    settle: State<Float>,
    logoSize: Dp,
    flourishes: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(logoSize * GlowExtent)
            .background(
                Brush.radialGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = GlowAlpha),
                        Color.Transparent,
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (flourishes) {
            LaunchSparkles(
                elapsedMillis = elapsedMillis,
                color = MaterialTheme.colorScheme.onBackground,
                logoSize = logoSize,
                modifier = Modifier.fillMaxSize(),
            )
        }

        LukiaMark(
            modifier = Modifier
                .size(logoSize)
                .graphicsLayer {
                    val expansion = 1f + LaunchTiming.ExitExpansion *
                        LaunchTiming.dissolve(elapsedMillis.value)

                    alpha = LukiaMotion.Standard.transform(
                        LaunchTiming.arrival(elapsedMillis.value),
                    )
                    scaleX = settle.value * expansion
                    scaleY = settle.value * expansion
                },
            // A closed mark that opens is the flourish; the open mark is the icon.
            fan = { if (flourishes) LaunchTiming.fan(elapsedMillis.value) else 1f },
        )
    }
}
