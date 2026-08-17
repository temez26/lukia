package io.lukia.core.presentation

import io.lukia.core.domain.AppError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppErrorDisplayTest {

    /**
     * Every case has to say something. A `when` that fell through to a blank
     * would leave the user staring at an error state with no text in it.
     */
    @Test
    fun everyErrorHasATitleAndAMessage() {
        val errors = listOf(
            AppError.DocumentNotFound,
            AppError.UnsupportedFileType,
            AppError.EmptyFolder,
            AppError.TranslationUnavailable,
            AppError.TranslationFailed,
            AppError.Unexpected("boom"),
            AppError.Unexpected(null)
        )

        errors.forEach { error ->
            assertTrue(error.displayTitle().isNotBlank(), "no title for $error")
            assertTrue(error.displayMessage().isNotBlank(), "no message for $error")
            assertTrue(error.displayShortMessage().isNotBlank(), "no short form for $error")
        }
    }

    @Test
    fun anUnexpectedErrorShowsItsCause() {
        assertEquals("boom", AppError.Unexpected("boom").displayMessage())
        assertEquals("boom", AppError.Unexpected("boom").displayShortMessage())
    }

    @Test
    fun anUnexpectedErrorWithoutACauseFallsBackToGenericWording() {
        val error = AppError.Unexpected(null)

        assertEquals("An unexpected error occurred.", error.displayMessage())
        assertEquals(error.displayTitle(), error.displayShortMessage())
    }
}
