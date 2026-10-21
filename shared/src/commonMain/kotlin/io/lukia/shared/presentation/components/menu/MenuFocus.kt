package io.lukia.shared.presentation.components.menu

/**
 * Whether an [AnchoredMenu] takes keyboard focus when it opens.
 *
 * True where there is a keyboard to drive it: a non-focusable popup never sees a
 * key, so on a desktop the arrow keys moved focus around the page behind an open
 * menu instead of through it. False on touch platforms, for the long-press
 * reason [AnchoredMenu] gives.
 */
internal expect val menusTakeFocus: Boolean
