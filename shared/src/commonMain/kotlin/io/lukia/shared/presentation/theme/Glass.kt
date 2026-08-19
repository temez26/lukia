package io.lukia.shared.presentation.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Tuning for the translucent "glass" chrome - the reader's toolbar and panels,
 * the menus, the circular chrome buttons - that sits above scrolling content.
 *
 * IMPORTANT - what this is, what it is not, and why it cannot be the real thing:
 *
 * Apple's Liquid Glass is a system material (`UIGlassEffect`, `.glassEffect()`).
 * JetBrains' own guidance is that a Compose Multiplatform app gets it only
 * through a native SwiftUI shell, because the system renders it through native
 * `TabView`, `NavigationStack` and toolbar APIs - which means it reaches an app's
 * tab bar and navigation bar, and nothing else. It cannot be applied to a
 * component Compose draws, because Compose does not draw with UIKit: it renders
 * its own tree through Skia. A button or a search field composed in Kotlin is
 * therefore out of its reach, whatever the shell around it.
 *
 * There is one escape hatch, and it is worth knowing its shape before reaching
 * for it. `UIKitInteropProperties(placedAsOverlay = true)` - experimental, since
 * Compose Multiplatform 1.10 - draws an interop view *above* the Compose canvas,
 * so a real `UIVisualEffectView` can sit over Compose content. Above, though, is
 * the whole problem: the native view covers whatever Compose drew beneath it, so
 * anything inside that glass has to be native too. It buys a natively blurred
 * bar containing native controls, not a Compose control on native glass. By
 * default interop goes the other way - underneath, with Compose punching a hole
 * to reveal it - which cannot carry Compose content on top either.
 *
 * `Modifier.blur()` is no substitute on any platform: it blurs a composable's
 * *own children*, not the backdrop behind it, and it is a no-op on Android below
 * API 31 (this app's minSdk is 24).
 *
 * So this is a hand-drawn approximation - layered translucency, a specular top
 * highlight and a hairline border - and it is one deliberately, on every
 * platform. It is tuned to be most pronounced on Apple platforms, where the look
 * is native, and more opaque elsewhere so the chrome stays legible without the
 * blur that would normally carry it.
 */
data class GlassStyle(
    /**
     * Opacity of a bar or a button - chrome that carries a glyph or a word or two
     * and nothing that has to be read for long.
     */
    val chromeAlpha: Float,
    /**
     * Opacity of a panel or a menu.
     *
     * Higher than [chromeAlpha], and close to opaque, because these carry rows of
     * text over a page that is still there behind them. Real glass stays legible
     * because the system blurs what is behind it into a wash; without that blur
     * the page's own type shows through the panel's, and every extra percent of
     * translucency is bought with legibility rather than depth. The gain is
     * biggest on Apple platforms, where the chrome was the most translucent to
     * begin with.
     */
    val panelAlpha: Float,
    /** Brightness of the specular highlight along the top edge. */
    val highlightAlpha: Float,
    /** Opacity of the hairline border. */
    val borderAlpha: Float,
    val shadowElevation: Dp,
)

/** More translucent, with a pronounced specular edge. */
internal val AppleGlassStyle = GlassStyle(
    chromeAlpha = 0.90f,
    panelAlpha = 0.98f,
    highlightAlpha = 0.45f,
    borderAlpha = 0.30f,
    shadowElevation = 12.dp,
)

/** Flatter and more opaque, closer to Material's elevated surfaces. */
internal val StandardGlassStyle = GlassStyle(
    chromeAlpha = 0.96f,
    panelAlpha = 1f,
    highlightAlpha = 0.18f,
    borderAlpha = 0.14f,
    shadowElevation = 6.dp,
)

/**
 * Resolved per platform: Apple targets get the translucent treatment, everything
 * else gets the flatter one.
 */
expect fun platformGlassStyle(): GlassStyle

val LocalGlassStyle = staticCompositionLocalOf { StandardGlassStyle }
