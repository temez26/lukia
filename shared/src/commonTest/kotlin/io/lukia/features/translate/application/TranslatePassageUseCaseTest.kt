package io.lukia.features.translate.application

import io.lukia.core.domain.AppError
import io.lukia.core.domain.toAppError
import io.lukia.features.translate.domain.TargetLanguage
import io.lukia.features.translate.domain.Translation
import io.lukia.features.translate.domain.TranslationRequest
import io.lukia.features.translate.domain.Translator
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TranslatePassageUseCaseTest {

    @Test
    fun aTranslatedPassageComesBackAsItWasTranslated() = runTest {
        val useCase = TranslatePassageUseCase { Translation(text = "Kutsukaa minua Ismaeliksi.") }

        val result = useCase(request())

        assertEquals("Kutsukaa minua Ismaeliksi.", result.getOrThrow().text)
    }

    @Test
    fun thePassageIsTidiedBeforeItIsSent() = runTest {
        var sent: String? = null
        val useCase = TranslatePassageUseCase { request ->
            sent = request.text
            Translation(text = "…")
        }

        useCase(request(text = "  Call me Ishmael.\n "))

        assertEquals("Call me Ishmael.", sent)
    }

    @Test
    fun surroundingWhitespaceIsTrimmedOffWhatComesBack() = runTest {
        val useCase = TranslatePassageUseCase {
            Translation(text = "\n Kutsukaa minua Ismaeliksi. ", note = "  ")
        }

        val translation = useCase(request()).getOrThrow()

        assertEquals("Kutsukaa minua Ismaeliksi.", translation.text)
        assertNull(translation.note, "a blank note is not a note")
    }

    @Test
    fun aNoteWorthMakingSurvives() = runTest {
        val useCase = TranslatePassageUseCase {
            Translation(text = "Hän veti aseensa.", note = "\"piece\" reads as a weapon here.")
        }

        assertEquals(
            "\"piece\" reads as a weapon here.",
            useCase(request()).getOrThrow().note
        )
    }

    /**
     * A blank answer is a provider failing without saying so. Passed on as a
     * success it would show as the sentence being replaced by nothing, which
     * reads as the reader having lost the text.
     */
    @Test
    fun anEmptyAnswerIsAFailureRatherThanAnEmptySentence() = runTest {
        val useCase = TranslatePassageUseCase { Translation(text = "   ") }

        assertEquals(AppError.TranslationFailed, useCase(request()).errorOrNull())
    }

    @Test
    fun thereIsNothingToTranslateInBlankText() = runTest {
        val useCase = TranslatePassageUseCase { error("should not be asked") }

        assertTrue(useCase(request(text = "   ")).isFailure)
    }

    /**
     * A text file with no sentence punctuation in it makes one enormous
     * "sentence"; sending it whole on a single tap helps nobody.
     */
    @Test
    fun anEnormousPassageIsRefusedRatherThanSent() = runTest {
        val useCase = TranslatePassageUseCase { error("should not be asked") }

        val result = useCase(request(text = "word ".repeat(1000)))

        assertTrue(result.isFailure)
        assertTrue((result.errorOrNull() as AppError.Unexpected).cause!!.contains("too long"))
    }

    /** A translator that throws is narrowed here rather than escaping upwards. */
    @Test
    fun aFailingTranslatorBecomesAnAppError() = runTest {
        val useCase = TranslatePassageUseCase { throw RuntimeException("no network") }

        assertEquals(AppError.Unexpected("no network"), useCase(request()).errorOrNull())
    }

    private fun request(text: String = "Call me Ishmael.") = TranslationRequest(
        text = text,
        targetLanguage = TargetLanguage.Finnish
    )

    private fun Result<*>.errorOrNull(): AppError? = exceptionOrNull()?.toAppError()
}

/** Reads better than an object at every call site, and the port is one function. */
private fun TranslatePassageUseCase(
    translate: suspend (TranslationRequest) -> Translation
) = TranslatePassageUseCase(Translator { translate(it) })
