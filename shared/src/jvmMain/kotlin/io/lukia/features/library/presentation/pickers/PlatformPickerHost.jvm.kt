package io.lukia.features.library.presentation.pickers

import androidx.compose.runtime.Composable
import io.lukia.features.library.presentation.browser.DesktopPicks
import io.lukia.features.library.presentation.browser.FileBrowserPanel

/**
 * The app's own browsing panel, on the desktops that have no system panel worth
 * opening - see [FileBrowserPanel].
 *
 * Draws nothing at all until something asks for a file, so it costs a null check
 * per composition to sit at the root of the app. macOS never asks: it opens
 * `NSOpenPanel` instead and answers from there.
 */
@Composable
actual fun PlatformPickerHost() {
    DesktopPicks.pending?.let { pick -> FileBrowserPanel(pick) }
}
