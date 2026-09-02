package io.lukia.features.translate.infrastructure.wiktionary

import io.lukia.features.translate.domain.lookup.WordMeaning

/**
 * How many other words an entry may hand back with itself.
 *
 * A page's derived terms and compounds run to whole columns on a common word,
 * and past a dozen they stop being a list somebody reads and become a list they
 * scroll past.
 */
private const val MAX_RELATED = 12

/**
 * How many parts of speech are worth keeping.
 *
 * `mean` is four sections in one language and that is close to the worst a real
 * page gets. The ceiling is here so that a page nothing else caught - a stub
 * with a heading every other line - cannot hand back a panel nobody can read.
 */
private const val MAX_MEANINGS = 6

/** One language's part of a page, read: what the word means and what it points at. */
internal data class WiktionaryReading(
    val meanings: List<WordMeaning>,
    val related: List<String>,
) {

    /** Every sense of every part of speech, in the order the page listed them. */
    val senses: List<String>
        get() = meanings.flatMap { it.senses }
}

/**
 * What one Wiktionary page actually says about [word].
 *
 * A page is every language that spells the word this way, each with its parts of
 * speech, its senses, and after them its inflections, its derived terms and its
 * references. What has to come out of it is one language's worth of that.
 *
 * One language and all of its parts of speech, which is the split that matters.
 * `mean` is an adjective that is `ilkeä`, a noun that is `keskiarvo` and a verb
 * that is `tarkoittaa`, and which one a sentence meant is settled by the
 * sentence - so a dictionary that keeps one of them has thrown away the answer
 * two times in three. The next language along is different: a word spelled the
 * same way in Spanish is not this word at all, and mixing the two would hand
 * back the Finnish for a French homograph.
 *
 * Three rules find the senses inside that, and the last two were learned the
 * hard way from a page that put `support (monikko supports)` on the page in
 * place of the sentence and left `kannatus` as a footnote:
 *
 *  - Only a section that could hold senses at all. A pronunciation read as a
 *    definition is how `IPA(key): /skɪlz/` ends up over a word - see
 *    [WikiSection.Kind.Aside].
 *  - A line that opens with the word being looked up is not a meaning of it.
 *    That is the headword and its inflections, and further down it is the
 *    derived terms - `support in decline` is a phrase the entry lists, not a
 *    sense.
 *  - Where the language ends, the entry ends. See [languageLevel].
 *
 * [limit] is how many senses one part of speech may keep, and it is what
 * separates the two things that ask this. A word being replaced in the line it
 * sits on can only carry a few before the paragraph stops being readable; a
 * reader who opened the entry on purpose asked for the entry.
 */
internal fun String.reading(word: String, limit: Int): WiktionaryReading {
    val sections = sections()
    val index = sections.indexOfFirst { it.holdsSensesOf(word) }

    // A word whose entry is nothing but its own headword - a loanword, a name -
    // has no section the rules above would take. Its own line is a poor answer
    // and it is better than telling the reader there is no entry when the page
    // is right there.
    if (index < 0) {
        return WiktionaryReading(
            meanings = listOf(WordMeaning(null, sections.anythingAtAll(limit))),
            related = emptyList(),
        )
    }

    val entry = sections.oneLanguageFrom(index)

    return WiktionaryReading(
        meanings = entry.meanings(word, limit),
        related = entry.related(),
    )
}

/**
 * The sections belonging to the language the senses were found in.
 *
 * A wiki gives each language a heading of its own and files everything about the
 * word underneath it, so the language boundary is the shallowest heading the
 * page uses - `== Englanti ==` against the `=== Verbi ===` and
 * `==== Etymologia ====` nested in it. Taken from the page rather than assumed,
 * because the section the senses came from can be nested arbitrarily deep: the
 * English Wiktionary buries its parts of speech under numbered etymologies.
 */
private fun List<WikiSection>.oneLanguageFrom(index: Int): List<WikiSection> {
    val boundary = languageLevel()

    return listOf(this[index]) + drop(index + 1).takeWhile { it.level > boundary }
}

/** The shallowest heading on the page, which is the one that names a language. */
private fun List<WikiSection>.languageLevel(): Int =
    filter { it.level > 0 }.minOfOrNull { it.level } ?: 0

private fun List<WikiSection>.meanings(word: String, limit: Int): List<WordMeaning> = asSequence()
    .filter { it.holdsSensesOf(word) }
    .take(MAX_MEANINGS)
    .map { WordMeaning(it.title.takeIf { title -> title.isNotEmpty() }, it.senses(word).take(limit)) }
    .toList()

private fun List<WikiSection>.related(): List<String> = asSequence()
    .filter { it.kind == WikiSection.Kind.Related }
    .flatMap { it.lines }
    .distinct()
    .take(MAX_RELATED)
    .toList()

private fun WikiSection.holdsSensesOf(word: String): Boolean =
    kind == WikiSection.Kind.Senses && senses(word).isNotEmpty()

/** What this section says the word means, with the entry's own furniture dropped. */
private fun WikiSection.senses(word: String): List<String> =
    lines.filterNot { it.isScaffoldingFor(word) }.distinct()

/** Whatever the page has, for an entry the rules above leave empty. */
private fun List<WikiSection>.anythingAtAll(limit: Int): List<String> = asSequence()
    .filter { it.kind != WikiSection.Kind.Aside }
    .flatMap { it.lines }
    .distinct()
    .take(limit)
    .toList()

/**
 * Whether this line is part of the entry rather than part of what the word
 * means.
 *
 * Matched on the opening word rather than on the whole line, because the
 * headword rarely stands alone: it carries its inflections after it, and the
 * derived terms are phrases built on the front of it.
 */
private fun String.isScaffoldingFor(word: String): Boolean {
    if (word.isEmpty()) return false
    if (!startsWith(word, ignoreCase = true)) return false

    // The word and nothing else is still the headword. What follows it, if
    // anything, has to be a break rather than more letters - otherwise
    // "supported" would be read as scaffolding for "support".
    val next = getOrNull(word.length) ?: return true

    return !next.isLetterOrDigit()
}
