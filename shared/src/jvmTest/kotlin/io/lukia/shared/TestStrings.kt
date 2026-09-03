package io.lukia.shared

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
