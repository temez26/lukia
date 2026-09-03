package io.lukia.shared

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

/**
 * A string as the app holds it, for a test that has to name a word before there
 * is a composition to read it in - `onNodeWithText` takes the word, not the
 * resource.
 *
 * Read out of the resources rather than written down again here. A test that
 * repeats the English asserts that two tables agree; this one asserts that the
 * screen shows what the app was given.
 */
fun words(resource: StringResource): String = runBlocking { getString(resource) }

/**
 * What [block] evaluates to inside a composition.
 *
 * A display mapper that reads a string resource is composable, so a test that
 * wants the words it produces has to compose it rather than call it. One scene
 * per value, which is slower than a plain call and is why this is only reached
 * for the mappers that genuinely need it.
 */
@OptIn(ExperimentalTestApi::class)
fun <T> composed(block: @Composable () -> T): T {
    var captured: Any? = null

    runComposeUiTest {
        setContent { captured = block() }
    }

    // Unchecked because T may itself be nullable - a mapper that answers null
    // for a book with no authors is a case this has to be able to return.
    @Suppress("UNCHECKED_CAST")
    return captured as T
}
