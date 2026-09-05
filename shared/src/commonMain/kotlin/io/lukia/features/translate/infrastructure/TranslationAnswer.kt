package io.lukia.features.translate.infrastructure

import io.lukia.features.translate.domain.Translation
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The translation, as every provider is asked to shape it. */
@Serializable
internal data class TranslatedPassage(
    val translation: String,
    val note: String = "",
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
    val body = answer.enclosedJsonObject()
        ?: translationFailed(TranslationFailureKind.NotATranslation)

    val passage = runCatching { json.decodeFromString<TranslatedPassage>(body) }
        .getOrElse { translationFailed(TranslationFailureKind.NotATranslation) }

    return Translation(
        text = passage.translation.trim(),
        note = passage.note.trim().takeIf { it.isNotEmpty() },
    )
}

/**
 * The same, for an answer that arrived a piece at a time and may not have
 * finished arriving.
 *
 * A dropped connection ends a stream exactly as a finished answer does, so
 * without [endedProperly] half a translation is indistinguishable from all a
 * model had to say - and the half that parses is the dangerous one, because it
 * goes into the book looking whole. What is asked here is the cheap version of
 * that question: an answer that still reads as a translation is taken, and one
 * that does not is blamed on the stream that stopped rather than on the model,
 * which is both true and the difference between a failure worth asking again
 * about and one that never will be.
 */
internal fun readStreamedPassage(
    answer: String,
    json: Json,
    endedProperly: Boolean,
): Translation {
    if (endedProperly) return readTranslatedPassage(answer, json)

    return runCatching { readTranslatedPassage(answer, json) }.getOrElse {
        translationFailed(
            kind = TranslationFailureKind.CutShort,
            explanation = "The translation stopped arriving partway through. Try again.",
        )
    }
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

/** The key whose value is the sentence, as it appears on the wire. */
private const val TRANSLATION_KEY = "\"translation\""

/**
 * As much of the translation as has arrived, out of an answer that is still
 * arriving.
 *
 * A provider streaming its reply sends the JSON object a few characters at a
 * time, so for most of the wait there is no object to parse - only a prefix of
 * one. Nothing that expects valid JSON can read that, which would leave the
 * reader looking at a blank space until the last character landed and make a
 * model writing steadily for five seconds indistinguishable from one that has
 * hung.
 *
 * So the value is read out by hand rather than parsed: found by its key,
 * unescaped as far as it goes, and cut off wherever the text currently ends.
 * Null until the key itself has arrived, and null for an answer that never
 * mentions it - a model writing a paragraph of prose has not begun translating
 * anything, and showing its preamble in place of the sentence would be worse
 * than showing nothing.
 *
 * What this returns is only ever a prefix of what [readTranslatedPassage] will
 * return from the finished answer. It deliberately does not judge whether the
 * answer was any good: that is still the parser's job, once there is a whole
 * one to judge.
 */
internal fun translationSoFar(answer: String): String? {
    val key = answer.indexOf(TRANSLATION_KEY)
    if (key < 0) return null

    val colon = answer.indexOf(':', key + TRANSLATION_KEY.length)
    if (colon < 0) return null

    val opening = answer.indexOf('"', colon + 1)
    if (opening < 0) return null

    return answer.unescapedStringFrom(opening + 1)
}

/**
 * The JSON string starting at [start], up to its closing quote or up to
 * wherever the text runs out - which while an answer is streaming is the
 * ordinary case rather than a malformed one.
 */
private fun String.unescapedStringFrom(start: Int): String = buildString {
    var index = start

    while (index < this@unescapedStringFrom.length) {
        val character = this@unescapedStringFrom[index]

        if (character == '"') return@buildString

        if (character != '\\') {
            append(character)
            index++
            continue
        }

        // An escape cut in half by the end of the buffer is a character that has
        // not finished arriving, not a broken one. Stopping short of it means the
        // next chunk shows it whole, rather than a stray backslash showing for a
        // moment in the middle of the sentence.
        val escape = this@unescapedStringFrom.escapeAt(index) ?: return@buildString

        append(escape.text)
        index += escape.length
    }
}

/** What one escape sequence stands for, and how much of the text it took up. */
private class Escape(val text: String, val length: Int)

private fun String.escapeAt(index: Int): Escape? {
    val marker = getOrNull(index + 1) ?: return null

    if (marker == 'u') {
        val digits = drop(index + 2).take(4)
        if (digits.length < 4) return null

        val code = digits.toIntOrNull(16) ?: return null

        return Escape(code.toChar().toString(), length = 6)
    }

    val text = when (marker) {
        'n' -> "\n"
        't' -> "\t"
        'r' -> "\r"
        'b' -> "\b"
        'f' -> "\u000C"
        // Quote, backslash and slash stand for themselves - and so does anything
        // else a lenient server escaped that it had no need to.
        else -> marker.toString()
    }

    return Escape(text, length = 2)
}
