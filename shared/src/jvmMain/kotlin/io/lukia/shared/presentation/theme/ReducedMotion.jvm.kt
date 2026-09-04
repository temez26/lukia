package io.lukia.shared.presentation.theme

import androidx.compose.runtime.Composable

/**
 * Nothing to ask.
 *
 * macOS has the setting and exposes it through `NSWorkspace`, which is AppKit
 * and so out of reach of a JVM process without a native bridge; Windows has
 * "Show animations" in the registry and Linux has whichever of a dozen desktop
 * settings the user happens to be running. None of the three is worth a JNI
 * dependency for an animation preference, and all three readers still have the
 * app's own setting.
 */
@Composable
actual fun systemReducesMotion(): Boolean = false
