package io.lukia.baselineprofile

import android.graphics.Point
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

/**
 * Getting to a book, which is what every benchmark here does before it can
 * measure anything.
 *
 * Shared between them rather than written twice: both open a real book out of a
 * real library, so both have to launch the app, get back out of whatever the
 * last iteration left open, narrow the library to one title and find its row -
 * and none of that is what either of them is measuring. A second copy of it
 * would be a second thing to fix every time the library's chrome moves.
 */

/** The package the benchmarks drive. */
internal const val PACKAGE = "io.lukia"

/**
 * Puts the library's search screen in front of the book called [title].
 *
 * Everything here is setup rather than measurement: finding a book among two
 * hundred of them is the library's work, and the benchmarks that call this are
 * about the reader.
 */
internal fun MacrobenchmarkScope.searchFor(title: String) {
    pressHome()
    startActivityAndWait()

    // The opening animation and the first read of the library both run here -
    // see LaunchSplash - so this sits through them rather than reaching for a
    // tab bar that has not arrived.
    device.wait(Until.hasObject(By.desc(SEARCH_TAB)), ARRIVAL_TIMEOUT_MILLIS)

    // An app killed inside a book comes back inside it, and a book has no tab
    // bar. Backing out of the one the last iteration opened is not part of what
    // is being measured.
    var attempts = 0

    while (!device.hasObject(By.desc(SEARCH_TAB)) && attempts++ < BACK_ATTEMPTS) {
        device.pressBack()
        device.wait(Until.hasObject(By.desc(SEARCH_TAB)), ARRIVAL_TIMEOUT_MILLIS)
    }

    // The term outlives the book being opened and closed, so every iteration
    // after the first finds its row already listed and types nothing.
    if (device.hasObject(By.clazz(RESULT_ROW).textContains(title))) return

    // By description rather than by text: the bar labels each tab as one node -
    // see `MobileNavigationBar` - and the word inside it is a label rather than
    // the thing that takes the press.
    device.findObject(By.desc(SEARCH_TAB)).click()
    device.wait(Until.hasObject(By.clazz(TEXT_FIELD)), ARRIVAL_TIMEOUT_MILLIS)

    // Typed at the field rather than written into it. Setting the text through
    // the accessibility node does not reach a Compose text field reliably, and a
    // search that never narrowed leaves nothing to press.
    device.findObject(By.clazz(TEXT_FIELD)).click()
    device.executeShellCommand("input text " + title.replace(" ", "%s"))

    // The list narrows as the term arrives, so what this waits for is the row
    // rather than a fixed pause.
    device.wait(
        Until.hasObject(By.clazz(RESULT_ROW).textContains(title)),
        ARRIVAL_TIMEOUT_MILLIS,
    )
}

/**
 * Where to press to open [title], as a point rather than as a node.
 *
 * A node goes stale the moment the list it is in is rebuilt, and this list is
 * rebuilt as the search narrows - so what is carried into a measurement is a
 * place on the screen, which cannot go stale. Read again on a stale node rather
 * than failing: the row is there, it is the handle on it that expired.
 */
internal fun UiDevice.rowCentre(title: String): Point {
    val row = By.clazz(RESULT_ROW).textContains(title)

    repeat(ROW_ATTEMPTS) {
        runCatching { wait(Until.findObject(row), ARRIVAL_TIMEOUT_MILLIS)?.visibleCenter }
            .getOrNull()
            ?.let { return it }
    }

    error("No row for “$title”. On screen: ${visibleText()}")
}

/** What is written on screen, for a failure that has to say where it got to. */
internal fun UiDevice.visibleText(): String =
    findObjects(By.textStartsWith("")).mapNotNull { it.text }.filter { it.isNotBlank() }
        .take(VISIBLE_TEXT_SAMPLE)
        .joinToString(" | ")

private const val SEARCH_TAB = "Search"

private const val TEXT_FIELD = "android.widget.EditText"

/** A row of the results, which is everything the field is not. */
private const val RESULT_ROW = "android.widget.TextView"

/** Enough of the screen to recognise it by, and not so much as to fill the log. */
private const val VISIBLE_TEXT_SAMPLE = 25

/** A stale row is worth reaching for again; a missing one is not. */
private const val ROW_ATTEMPTS = 3

/** Deep enough to leave a book, shallow enough not to leave the app. */
private const val BACK_ATTEMPTS = 3

private const val ARRIVAL_TIMEOUT_MILLIS = 10_000L
