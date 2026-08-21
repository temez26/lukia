package io.lukia.features.translate.application

import io.lukia.core.domain.AppError
import io.lukia.core.domain.asFailure
import io.lukia.core.domain.runCatchingApp
import io.lukia.features.translate.domain.MAX_TRANSLATABLE_CHARS
import io.lukia.features.translate.domain.Translation
import io.lukia.features.translate.domain.TranslationRequest
import io.lukia.features.translate.domain.Translator

/**
 * Translates one passage, and is the only thing above the domain that knows a
 * translator exists at all.
 *
 * Everything here is about the passage rather than about any provider: the
 * guards hold whether the words go to a model, to an API or to a dictionary,
 * and so does the shape of the failure when they do not come back.
 */
class TranslatePassageUseCase(
    private val translator: Translator,
) {

    /**
     * [onPartial] is passed straight through to whatever is translating, and is
     * the caller's business rather than this one's: nothing here can improve on
     * a piece of a sentence, and the guards that matter - a passage too long, an
     * answer that came back blank - are about the whole of it.
     */
    suspend operator fun invoke(
        request: TranslationRequest,
        onPartial: (String) -> Unit = {},
    ): Result<Translation> {
        val passage = request.text.trim()

        if (passage.isEmpty()) {
            return AppError.Unexpected("There is nothing to translate here.").asFailure()
        }

        if (passage.length > MAX_TRANSLATABLE_CHARS) {
            return AppError.Unexpected("That passage is too long to translate.").asFailure()
        }

        val translation =
            runCatchingApp { translator.translate(request.copy(text = passage), onPartial) }
                .getOrElse { return Result.failure(it) }

        // A provider that answered with nothing has failed without saying so.
        // Left as a success it would show as a sentence replaced by a blank
        // space, which reads as the reader having lost the text rather than as
        // the translation having failed.
        if (translation.text.isBlank()) {
            return AppError.TranslationFailed.asFailure()
        }

        return Result.success(
            Translation(
                text = translation.text.trim(),
                note = translation.note?.trim()?.takeIf { it.isNotEmpty() },
            ),
        )
    }
}
