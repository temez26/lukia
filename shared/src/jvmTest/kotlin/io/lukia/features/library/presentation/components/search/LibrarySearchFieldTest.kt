package io.lukia.features.library.presentation.components.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Escape in the library's search field, as a desktop search field answers it. */
@OptIn(ExperimentalTestApi::class)
class LibrarySearchFieldTest {

    private var query by mutableStateOf("dune")

    @Test
    fun escapeEmptiesTheFieldThenLeavesIt() = runComposeUiTest {
        setContent {
            LibrarySearchField(query = query, onQueryChange = { query = it }, placeholder = "")
        }
        val field = onNode(hasSetTextAction())
        field.performClick()
        field.assertIsFocused()

        field.performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        assertEquals("", query)
        field.assertIsFocused()

        field.performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        field.assertIsNotFocused()
    }
}
