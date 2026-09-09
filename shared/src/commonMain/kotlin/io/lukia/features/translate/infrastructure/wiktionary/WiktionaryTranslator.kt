package io.lukia.features.translate.infrastructure.wiktionary

import io.lukia.core.domain.AppError
import io.lukia.core.domain.AppErrorException
import io.lukia.features.translate.domain.Translation
import io.lukia.features.translate.domain.TranslationRequest
import io.lukia.features.translate.domain.Translator
import io.lukia.features.translate.domain.lookup.WordEntry

/**
 * How many senses of one part of speech are read off the page.
 *
 * A dictionary's whole advantage over a translator is that it does not choose,
 * and its whole risk is that it does not choose: a common word has a dozen
 * entries, and a dozen entries spliced into a sentence is not something anybody
 * can read. The first few answer most of the time, and the reader who wanted the
 * rest can open the entry - see [WiktionaryDictionary].
 */
private const val MAX_SENSES = 3

/**
 * How many readings go under the paragraph with it.
 *
 * The others are an aside about the word rather than a second thing to read in
 * place of it, and an aside that runs past a line is one nobody finishes.
 */
private const val MAX_REMARKED = 2

/**
 * Looks a word up in Wiktionary, in the language the reader is reading into.
 *
 * A dictionary rather than a translator, which is the point of offering it: it
 * gives the senses a word has instead of choosing one, which is often what
 * somebody learning a language wants from a word they do not know.
 *
 * Which wikis are asked and how the page is read are [WiktionaryPages]' and
 * [reading]'s business. What is left here is the one decision this has to make -
 * how much of an entry fits over a word in a book, which is not much.
 */
internal class WiktionaryTranslator(
    private val pages: WiktionaryPages,
) : Translator {

    override suspend fun translate(
        request: TranslationRequest,
        onPartial: (String) -> Unit,
    ): Translation {
        val word = request.text.trim()

        val entry = pages.entryFor(word, request.targetLanguage, MAX_SENSES)

        if (entry == null || entry.hasNoMeaning) {
            throw AppErrorException(AppError.Unexpected(entry.whyNothingFits(word)))
        }

        val readings = entry.readings()

        return Translation(
            text = readings.first(),
            // The rest go where a translator's remark goes, under the paragraph:
            // a dictionary's second meaning is an aside about the word, not a
            // second thing to read in place of it.
            note = readings.drop(1)
                .take(MAX_REMARKED)
                .takeIf { it.isNotEmpty() }
                ?.joinToString("  \u2022  "),
        )
    }
}

/**
 * The few words worth offering for this entry, best first.
 *
 * One reading of each part of speech before a second reading of any of them,
 * because that is the axis the reader cannot see from the page. `mean` is
 * `ilkeä`, `keskiarvo` and `tarkoittaa`, and a remark that answered `ilkeä`,
 * `viheliäinen`, `saita` would be three ways of saying the reading this happened
 * to pick was the right one - which, handed one word and no sentence, it has no
 * way of knowing.
 *
 * Topped up from the first part of speech's other senses, so that a word with
 * only one part of speech still has its synonyms offered rather than nothing.
 */
private fun WordEntry.readings(): List<String> = (
    meanings.mapNotNull { it.senses.firstOrNull() } +
        meanings.firstOrNull()?.senses.orEmpty().drop(1)
    )
    .map { it.oneReading() }
    .distinct()

/**
 * The one word this sense comes down to.
 *
 * A dictionary writes a sense the way a dictionary does - `taito, kyky,
 * kyvykkyys`, `(matematiikka) keskiarvo` - and all of that standing where one
 * word stood is a sentence nobody can read. So the qualifier saying which field
 * the sense belongs to is dropped, and the synonyms after the first are left to
 * the panel, which has room to list them.
 *
 * Which reading is kept is the first one the page wrote and nothing cleverer. A
 * word has as many meanings as parts of speech, and which a sentence meant is
 * settled by the sentence - so a dictionary handed one word and no sentence
 * cannot be right about it. What it can be is short, and honest that there is
 * more: see [WordEntry.meanings], which the panel shows in full.
 */
private fun String.oneReading(): String {
    val defined = substringAfter(')', missingDelimiterValue = this)
        .takeIf { trimStart().startsWith('(') }
        ?: this

    return defined.takeWhile { it !in SENSE_BREAKS }.trim().ifEmpty { trim() }
}

/** What a dictionary puts between one reading of a sense and the next. */
private val SENSE_BREAKS = charArrayOf(',', ';', '/')

/**
 * What to say under the paragraph when the dictionary has nothing to put over
 * the word.
 *
 * The two cases read as the same failure and are not one. A word no wiki has
 * heard of is the end of it; a word whose page says what form it is, of a base
 * form that has no entry either, has been answered - just not with a meaning,
 * and not with anything worth splicing into a sentence. The grammar is waiting
 * in the panel, so what belongs here is the way in to it, in the same words the
 * settings use for the gesture.
 */
private fun WordEntry?.whyNothingFits(word: String): String = when {
    this?.form != null -> "No direct translation \u2014 hold the word for more about it."
    else -> "No dictionary entry for \u201c$word\u201d."
}
