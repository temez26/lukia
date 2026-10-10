package io.lukia.core.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import io.lukia.core.domain.AppError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Composed rather than called, because reading a string resource takes a
 * composition - which is also why this sits in `jvmTest` rather than beside the
 * rest of the core tests: the Compose test runtime only reaches the classpath
 * here. See the `jvmTest` dependencies in `shared/build.gradle.kts`.
 */
@OptIn(ExperimentalTestApi::class)
class AppErrorDisplayTest {

    /**
     * Every case has to say something. A `when` that fell through to a blank
     * would leave the user staring at an error state with no text in it.
     */
    @Test
    fun everyErrorHasATitleAndAMessage() = runComposeUiTest {
        val said = mutableMapOf<AppError, List<String>>()

        setContent {
            Errors.forEach { error ->
                said[error] = listOf(
                    error.displayTitle(),
                    error.displayMessage(),
                    error.displayShortMessage(),
                )
            }
        }

        Errors.forEach { error ->
            said.getValue(error).forEach {
                assertTrue(it.isNotBlank(), "nothing to say about $error")
            }
        }
    }

    @Test
    fun anUnexpectedErrorShowsItsExplanation() = runComposeUiTest {
        val error = AppError.Unexpected("boom")
        var message = ""
        var short = ""

        setContent {
            message = error.displayMessage()
            short = error.displayShortMessage()
        }

        assertEquals("boom", message)
        assertEquals("boom", short)
    }

    @Test
    fun anUnexpectedErrorWithoutAnExplanationFallsBackToGenericWording() = runComposeUiTest {
        val error = AppError.Unexpected(null)
        var title = ""
        var message = ""
        var short = ""

        setContent {
            title = error.displayTitle()
            message = error.displayMessage()
            short = error.displayShortMessage()
        }

        assertEquals("An unexpected error occurred.", message)
        assertEquals(title, short)
    }

    private companion object {
        val Errors = listOf(
            AppError.DocumentNotFound,
            AppError.UnsupportedFileType,
            AppError.FormatNotReadable,
            AppError.EmptyFolder,
            AppError.TranslationUnavailable,
            AppError.TranslationFailed,
            AppError.Unexpected("boom"),
            AppError.Unexpected(null),
        )
    }
}
