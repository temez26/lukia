package io.lukia.features.translate.infrastructure

import io.lukia.core.domain.AppError
import io.lukia.core.domain.AppErrorException
import io.lukia.features.translate.domain.Translation
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The translation, as every provider is asked to shape it. */
@Serializable
internal data class TranslatedPassage(
    val translation: String,
    val note: String = ""
)

/**
 * Reads a translation out of whatever a provider actually sent back.
 *
 * A provider held to a schema answers with exactly this object and nothing
 * else, and the forgiveness below costs nothing. A model on somebody's laptop
 * may not be: it wraps the object in a code fence, or introduces it, or adds a
 * closing remark. None of that is a failure worth showing a reader who only
 * wanted a sentence translated, so the object is found inside whatever came
 * back rather than demanded to be all of it.
 *
 * What is not forgiven is an answer with no object in it at all. That is a
 * model that did not do what was asked, and pretending its prose is a
 * translation would put something into the book that nobody vouched for.
 */
internal fun readTranslatedPassage(answer: String, json: Json): Translation {
    val body = answer.enclosedJsonObject() ?: throw AppErrorException(AppError.TranslationFailed)

    val passage = runCatching { json.decodeFromString<TranslatedPassage>(body) }
        .getOrElse { throw AppErrorException(AppError.TranslationFailed) }

    return Translation(
        text = passage.translation.trim(),
        note = passage.note.trim().takeIf { it.isNotEmpty() }
    )
}

/**
 * The outermost `{...}` in this text, or null when there is none.
 *
 * Taken from the first brace to the last rather than parsed: what is being
 * skipped is a code fence or a sentence of preamble, and both sit outside the
 * object. Anything between those braces that is not valid JSON fails at the
 * next step, which is where it should.
 */
private fun String.enclosedJsonObject(): String? {
    val start = indexOf('{')
    val end = lastIndexOf('}')

    if (start < 0 || end <= start) return null

    return substring(start, end + 1)
}
