package io.lukia.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test

/**
 * Writes the list of methods worth compiling ahead of time, and the shorter list
 * of the ones a launch cannot start without.
 *
 * Walks the first few seconds of a cold start: the app opens, the shelf
 * arrives, and the reader moves along the tab bar. Every class loaded and method
 * run on that path goes into `baseline-prof.txt`, and the installer compiles
 * them at install time instead of leaving the JIT to catch up while somebody is
 * watching.
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
 * The same walk also writes the *startup* profile - see [includeInStartupProfile]
 * below - which is put to a different use: the baseline profile tells the
 * installer what to compile, the startup profile tells R8 how to lay the dex
 * files out so the classes a launch touches sit together rather than scattered
 * and read a page at a time. It needs `dexLayoutOptimization` in :androidApp to
 * be used at all.
 *
 * Reading a book is not covered: the generator walks an empty library - a fresh
 * emulator has imported nothing - so nothing here reaches the reader. Covering
 * it would need a PDF shipped beside this and a provider to hand it over.
 */
class StartupProfile {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun openingAndBrowsing() = rule.collect(
        packageName = PACKAGE,
        // What this walk is: the app opening. Without it the run writes a
        // baseline profile and no startup profile at all, and the dex layout
        // has nothing to order itself by.
        includeInStartupProfile = true,
    ) {
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
