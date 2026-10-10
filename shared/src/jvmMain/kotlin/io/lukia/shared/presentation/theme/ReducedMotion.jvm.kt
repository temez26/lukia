package io.lukia.shared.presentation.theme

import androidx.compose.runtime.Composable

/**
 * Nothing to ask.
 *
 * macOS exposes the setting through `NSWorkspace`, which is AppKit and out of
 * reach of a JVM process without a native bridge; Windows has "Show animations"
 * in the registry, Linux whichever of a dozen desktop settings is running. None
 * is worth a JNI dependency for an animation preference, and all three readers
 * still have the app's own setting.
 */
@Composable
actual fun systemReducesMotion(): Boolean = false
