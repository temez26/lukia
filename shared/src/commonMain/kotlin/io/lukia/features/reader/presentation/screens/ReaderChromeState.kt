package io.lukia.features.reader.presentation.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * What the reader has open on top of the book, and whether the chrome is showing.
 *
 * One object rather than three booleans because the three are not independent:
 * at most one panel may be open, and the toolbar cannot leave while one is - a
 * panel hangs off it and would be stranded. Kept together, those rules are
 * written once here instead of at each of the half-dozen places that toggle one.
 */
@Stable
internal class ReaderChromeState {

    var chromeVisible by mutableStateOf(true)
        private set

    var settingsExpanded by mutableStateOf(false)
        private set

    var contentsExpanded by mutableStateOf(false)
        private set

    val panelOpen: Boolean get() = settingsExpanded || contentsExpanded

    /** A panel hangs off the toolbar, so the toolbar cannot leave while one is open. */
    val chromeShown: Boolean get() = chromeVisible || panelOpen

    fun showChrome() {
        chromeVisible = true
    }

    fun hideChrome() {
        chromeVisible = false
    }

    fun toggleChrome() {
        chromeVisible = !chromeVisible
    }

    fun toggleContents() {
        settingsExpanded = false
        contentsExpanded = !contentsExpanded
    }

    fun toggleSettings() {
        contentsExpanded = false
        settingsExpanded = !settingsExpanded
    }

    fun closePanels() {
        settingsExpanded = false
        contentsExpanded = false
    }
}

@Composable
internal fun rememberReaderChromeState(): ReaderChromeState = remember { ReaderChromeState() }
