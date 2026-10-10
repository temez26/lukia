package io.lukia.features.translate.application

import io.lukia.core.domain.AppError
import io.lukia.core.domain.asFailure
import io.lukia.core.domain.runCatchingApp
import io.lukia.features.translate.domain.lookup.WordDictionary
import io.lukia.features.translate.domain.lookup.WordEntry
import io.lukia.features.translate.domain.settings.TargetLanguage

/**
 * Asks a dictionary what one word means, at whatever length it has to say.
 *
 * The counterpart of [TranslatePassageUseCase] and deliberately not part of it:
 * that one is about a passage and ends in something to put on the page in place
 * of it, while this one is about a word and ends in everything known about it.
 * What they share is the shape of a failure, which is why both narrow one here
 * rather than letting a transport exception reach a screen.
 */
class LookUpWordUseCase(
    private val dictionary: WordDictionary,
) {

    suspend operator fun invoke(
        word: String,
        targetLanguage: TargetLanguage,
    ): Result<WordEntry> {
        val term = word.trim()

        if (term.isEmpty()) {
            return AppError.Unexpected("There is no word here to look up.").asFailure()
        }

        return runCatchingApp { dictionary.define(term, targetLanguage) }
            .fold(onSuccess = { it.withRealSenses() }, onFailure = { Result.failure(it) })
    }
}

/**
 * The entry with nothing empty in it, or a failure when that leaves nothing at
 * all - a dictionary that answered with blank lines has failed without saying
 * so, the same reasoning as a translation that came back blank.
 *
 * A word with no senses but a form is not nothing: what the reader held is an
 * inflection of a word the dictionary has no page for, and naming the inflection
 * is the whole of what this panel can offer - see [WordEntry.form].
 */
private fun WordEntry.withRealSenses(): Result<WordEntry> {
    val real = meanings
        .map { meaning -> meaning.copy(senses = meaning.senses.map { it.trim() }.filter { it.isNotEmpty() }) }
        .filter { it.senses.isNotEmpty() }

    if (real.isEmpty() && form.isNullOrBlank()) {
        return AppError.Unexpected("No dictionary entry for “$word”.").asFailure()
    }

    return Result.success(copy(meanings = real))
}
