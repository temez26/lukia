package io.lukia.shared.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled
import platform.UIKit.UIAccessibilityReduceMotionStatusDidChangeNotification

/**
 * Settings > Accessibility > Motion > Reduce Motion, which is the switch Apple
 * expects every app to read and the one this app was ignoring.
 *
 * Subscribed to as well as read. iOS posts a notification the moment the switch
 * is thrown, and the app is normally still on screen behind Settings when that
 * happens.
 */
@Composable
actual fun systemReducesMotion(): Boolean {
    var reduced by remember { mutableStateOf(UIAccessibilityIsReduceMotionEnabled()) }

    DisposableEffect(Unit) {
        val observer = NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIAccessibilityReduceMotionStatusDidChangeNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            reduced = UIAccessibilityIsReduceMotionEnabled()
        }

        onDispose { NSNotificationCenter.defaultCenter.removeObserver(observer) }
    }

    return reduced
}
