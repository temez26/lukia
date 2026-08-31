package io.lukia.shared.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.lukia.core.domain.AppAccent
import io.lukia.core.domain.AppMotion
import io.lukia.core.domain.AppPalette
import io.lukia.getPlatform

/**
 * The app's corner radii, and the only place any of them is decided.
 *
 * Evenly stepped in the spirit of Apple's rounded rects, but tighter than they
 * were: every radius used to be written at its call site, which produced 8, 11,
 * 14, 16 and 18dp corners on elements that belong to the same family, and left
 * the whole app rounder than a desktop window wants to be.
 *
 * - [Shapes.extraSmall] a badge or a chip
 * - [Shapes.small] a field, a button, a stepper
 * - [Shapes.medium] a menu, a card, a cover
 * - [Shapes.large] a floating panel, a grouped list
 * - [Shapes.extraLarge] an overlay panel, which is the largest thing that floats
 */
internal val LukiaShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

/**
 * [palette] and [accent] default to what the app ships with, so a preview or a
 * test composes the same colours a first run does without having to say so.
 *
 * [touch] decides how large every control is drawn - see [LukiaMetrics]. It
 * defaults to what the platform says rather than to what the window measures,
 * because a finger does not get smaller when a tablet is turned on its side. A
 * parameter rather than a straight lookup so a test can compose either.
 */
@Composable
fun LukiaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    palette: AppPalette = AppPalette.Crisp,
    accent: AppAccent = AppAccent.Themed,
    motion: AppMotion = AppMotion.Standard,
    touch: Boolean = getPlatform().isMobile,
    content: @Composable () -> Unit,
) {
    val colorScheme = remember(darkTheme, palette, accent) {
        colorSchemeFor(palette, darkTheme, accent)
    }
    val glassStyle = remember { platformGlassStyle() }
    val motionStyle = remember(motion) { motionStyleFor(motion) }

    // Measured once at the root so every screen can lay itself out against the
    // real window size instead of assuming a phone. Both axes: a phone in
    // landscape is as wide as a tablet and shorter than anything else the app
    // runs in, and width alone cannot tell those two apart.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val widthClass = WindowWidthClass.fromWidth(maxWidth)
        val heightClass = WindowHeightClass.fromHeight(maxHeight)

        CompositionLocalProvider(
            LocalLukiaDimens provides dimensFor(widthClass),
            LocalLukiaMetrics provides metricsFor(touch),
            LocalWindowWidthClass provides widthClass,
            LocalWindowHeightClass provides heightClass,
            LocalGlassStyle provides glassStyle,
            LocalMotionStyle provides motionStyle,
            // Material's own floor, told what this app's floor is.
            //
            // The modifier behind it expands what a control can be *hit* on
            // without changing what is drawn, which is exactly the trade a
            // 32dp mark beside a search term needs: keep the small ✕, give the
            // thumb something to aim at. Material defaults it to its own 48dp;
            // this app has decided on Apple's 44 and sizes every other control
            // to it, so leaving the two floors different would mean one number
            // for the controls the app draws and another for the ones it
            // borrows.
            //
            // Nothing at all where a mouse is doing the pointing: a Switch
            // reserving 48dp of a panel whose rows are 30dp is precisely the
            // stretched-phone look [LukiaMetrics] exists to avoid.
            LocalMinimumInteractiveComponentSize provides
                if (touch) MinTouchTarget else 0.dp,
        ) {
            MaterialTheme(
                colorScheme = colorScheme,
                typography = LukiaTypography,
                shapes = LukiaShapes,
                content = content,
            )
        }
    }
}
