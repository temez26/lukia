package io.lukia.features.reader.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * Set on the view rather than as a window flag.
 *
 * `keepScreenOn` reaches the same flag on whatever window the view is attached
 * to, without the screen having to find an Activity - which it cannot honestly
 * do from Compose, where the context handed to it may be a wrapper or may not be
 * an Activity at all.
 *
 * Cleared on the way out rather than left set, because the flag belongs to the
 * window and the window outlives this screen.
 */
@Composable
actual fun KeepScreenAwake(enabled: Boolean) {
    val view = LocalView.current

    DisposableEffect(view, enabled) {
        view.keepScreenOn = enabled

        onDispose { view.keepScreenOn = false }
    }
}
