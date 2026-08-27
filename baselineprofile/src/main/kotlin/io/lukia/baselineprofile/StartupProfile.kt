package io.lukia.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test

/**
 * Writes the list of methods worth compiling ahead of time.
 *
 * What this walks is what a reader does in the first few seconds of a cold
 * start: the app opens, the shelf arrives, and they move along the tab bar. Every
 * class loaded and method run on that path goes into `baseline-prof.txt`, and the
 * installer compiles them at install time instead of leaving the JIT to catch up
 * while somebody is watching.
 *
 * It is generated rather than written:
 *
 * ```
 * ANDROID_SERIAL=<emulator> ./gradlew :androidApp:generateBaselineProfile
 * ```
 *
 * on an emulator rather than a phone, because generating installs a
 * release-signed build of the app - which on a device that already has a debug
 * one means uninstalling it, and taking somebody's library with it.
 *
 * A stale profile is not wrong, only less useful: it names methods that still
 * exist and misses ones that are new. Worth regenerating when the opening
 * changes shape.
 *
 * What it does not cover is reading a book. The generator walks a library that
 * is empty - a fresh emulator has imported nothing - so the reader is reached
 * by nothing here. Covering it needs a book to open, which means a PDF shipped
 * beside this and a provider to hand it over with; the paths below are the ones
 * every launch takes, which is what a baseline profile is mostly for.
 */
class StartupProfile {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun openingAndBrowsing() = rule.collect(packageName = PACKAGE) {
        pressHome()
        startActivityAndWait()

        // The opening animation and the first read of the library both run here
        // - see LaunchSplash - so the profile has to sit through them rather
        // than measure a screen that has not arrived.
        device.waitForIdle()
        device.wait(Until.hasObject(By.desc(HOME_TAB)), ARRIVAL_TIMEOUT_MILLIS)

        // By description rather than by text: the bar labels each tab as one
        // node - see `MobileNavigationBar`, which merges the mark and the word
        // so a screen reader announces them together - and that merged node is
        // the one that takes the press. The word on its own is a label inside it
        // and is not clickable.
        listOf(LIBRARY_TAB, SEARCH_TAB, HOME_TAB).forEach { tab ->
            device.findObject(By.desc(tab))?.click()
            device.wait(Until.hasObject(By.desc(tab)), ARRIVAL_TIMEOUT_MILLIS)
            device.waitForIdle()

            // Scrolling is what composes the rows past the first screenful, and
            // a shelf of covers is the most expensive thing the app draws.
            device.findObject(By.scrollable(true))?.let { list ->
                list.setGestureMargin(device.displayWidth / GESTURE_MARGIN)
                list.scroll(Direction.DOWN, SCROLL_FRACTION)
                device.waitForIdle()
            }
        }
    }

    private companion object {
        const val PACKAGE = "io.lukia"

        const val HOME_TAB = "Home"
        const val SEARCH_TAB = "Search"
        const val LIBRARY_TAB = "Library"

        const val ARRIVAL_TIMEOUT_MILLIS = 10_000L

        /** Keeps the swipe clear of the system's own edge gestures. */
        const val GESTURE_MARGIN = 5

        const val SCROLL_FRACTION = 0.8f
    }
}
