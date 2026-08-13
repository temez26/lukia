package io.lukia.shared.presentation.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Tuning for the translucent "glass" chrome (floating action button, detail
 * top bar) that sits above scrolling content.
 *
 * IMPORTANT - what this is and is not:
 *
 * Apple's Liquid Glass is a UIKit/SwiftUI system material (`UIGlassEffect`,
 * `.glassEffect()`). Compose Multiplatform does not draw with UIKit; it renders
 * its own tree through Skia, so that system material cannot be applied to
 * Compose content. Nor can we fall back to `Modifier.blur()`: that blurs a
 * composable's *own children*, not the backdrop behind it, and it is a no-op on
 * Android below API 31 (this app's minSdk is 24).
 *
 * So this is a hand-drawn approximation - layered translucency, a specular top
 * highlight and a hairline border - not a real backdrop blur. It is tuned to be
 * most pronounced on Apple platforms, where the look is native, and more opaque
 * elsewhere so the chrome stays legible without the blur that would normally
 * carry it. See the notes in the pull request for the two routes to real blur.
 */
data class GlassStyle(
    /** Opacity of the chrome fill. Lower reads as more glass-like. */
    val surfaceAlpha: Float,
    /** Brightness of the specular highlight along the top edge. */
    val highlightAlpha: Float,
    /** Opacity of the hairline border. */
    val borderAlpha: Float,
    val shadowElevation: Dp
)

/** More translucent, with a pronounced specular edge. */
internal val AppleGlassStyle = GlassStyle(
    surfaceAlpha = 0.72f,
    highlightAlpha = 0.45f,
    borderAlpha = 0.30f,
    shadowElevation = 12.dp
)

/** Flatter and more opaque, closer to Material's elevated surfaces. */
internal val StandardGlassStyle = GlassStyle(
    surfaceAlpha = 0.92f,
    highlightAlpha = 0.18f,
    borderAlpha = 0.14f,
    shadowElevation = 6.dp
)

/**
 * Resolved per platform: Apple targets get the translucent treatment, everything
 * else gets the flatter one.
 */
expect fun platformGlassStyle(): GlassStyle

val LocalGlassStyle = staticCompositionLocalOf { StandardGlassStyle }
