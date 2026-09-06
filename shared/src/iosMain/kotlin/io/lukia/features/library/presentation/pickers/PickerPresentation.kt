package io.lukia.features.library.presentation.pickers

import platform.UIKit.UIApplication
import platform.UIKit.UIScene
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene

/**
 * Presents [viewController] over whatever the reader is currently looking at.
 *
 * Written once because both pickers need it and both were finding the window
 * the same wrong way: `UIApplication.windows` is deprecated from iOS 15 and
 * answers every window the process has in no promised order, so its first entry
 * is the app's own only until something else - a keyboard, an alert, a system
 * sheet - puts a window of its own in front. Presenting from the wrong one is
 * not an error; it is a picker that never appears, and a reader who pressed
 * "Add books" and got nothing.
 *
 * Asked of the foreground scene instead, which is the answer that has been right
 * since iOS 13 and is the only one Apple offers now.
 *
 * The chain of already-presented controllers is walked to the end for the same
 * reason. Both pickers can be reached from inside the settings sheet, and UIKit
 * refuses - silently - to present a second thing from a controller that is
 * already presenting one.
 */
internal fun presentOverApp(viewController: UIViewController) {
    val top = topViewController() ?: return

    top.presentViewController(viewController, animated = true, completion = null)
}

private fun topViewController(): UIViewController? {
    var controller = keyWindow()?.rootViewController ?: return null

    while (true) {
        controller = controller.presentedViewController ?: return controller
    }
}

/**
 * The window the app is actually showing.
 *
 * A scene that is not foreground-active belongs to another window of the same
 * app on iPad, or to one the system has put to sleep, and presenting into it
 * puts the picker somewhere nobody is looking.
 */
private fun keyWindow(): UIWindow? {
    val scenes = UIApplication.sharedApplication.connectedScenes
        .filterIsInstance<UIWindowScene>()

    val active = scenes.firstOrNull { it.isForegroundActive() } ?: scenes.firstOrNull()

    return active?.windows
        ?.filterIsInstance<UIWindow>()
        ?.let { windows -> windows.firstOrNull { it.isKeyWindow() } ?: windows.firstOrNull() }
}

private fun UIScene.isForegroundActive(): Boolean =
    activationState == UISceneActivationStateForegroundActive
