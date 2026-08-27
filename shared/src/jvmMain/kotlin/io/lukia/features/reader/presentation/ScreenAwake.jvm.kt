package io.lukia.features.reader.presentation

import androidx.compose.runtime.Composable

/**
 * Nothing to ask the desktop.
 *
 * A desktop reader is one window among many, and the JVM has no portable way to
 * hold off a screensaver - what exists is per-platform native calls, for a
 * setting the reader is not offered on the desktop in the first place.
 */
@Composable
actual fun KeepScreenAwake(enabled: Boolean) = Unit
