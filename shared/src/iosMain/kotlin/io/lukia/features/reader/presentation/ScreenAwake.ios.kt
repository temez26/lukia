package io.lukia.features.reader.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import platform.UIKit.UIApplication

/**
 * The idle timer is the whole of iOS's answer here: disabled, the device stops
 * counting down to dimming and locking, and it starts again the moment it is
 * put back - which is what leaving the book does.
 *
 * A process-wide switch rather than a property of a window, so it is put back on
 * the way out rather than left for the next screen to notice.
 */
@Composable
actual fun KeepScreenAwake(enabled: Boolean) {
    DisposableEffect(enabled) {
        UIApplication.sharedApplication.idleTimerDisabled = enabled

        onDispose { UIApplication.sharedApplication.idleTimerDisabled = false }
    }
}
