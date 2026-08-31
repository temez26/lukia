package io.lukia.features.library.presentation.pickers

import androidx.compose.runtime.Composable

/**
 * Whatever the platform's picker has to draw inside the app, if anything.
 *
 * Composed once at the root - see `App` - rather than beside the buttons that
 * open a picker, because there is one of it and it covers the whole window.
 *
 * Nothing at all on the platforms whose picker is the system's own: Android
 * hands the choosing to a document provider, iOS to a document picker, and macOS
 * to `NSOpenPanel`. Only the desktop platforms with no panel worth opening draw
 * one of their own here.
 */
@Composable
expect fun PlatformPickerHost()
