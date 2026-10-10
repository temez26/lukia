package io.lukia.features.library.presentation.pickers

import androidx.compose.runtime.Composable

/**
 * Whatever the platform's picker has to draw inside the app, if anything.
 *
 * Composed once at the root - see `App` - rather than beside the buttons that
 * open a picker, because there is one of it and it covers the whole window.
 *
 * Nothing on the platforms whose picker is the system's own - Android's document
 * provider, iOS's document picker, macOS's `NSOpenPanel`. Only the desktops with
 * no panel worth opening draw one here.
 */
@Composable
expect fun PlatformPickerHost()
