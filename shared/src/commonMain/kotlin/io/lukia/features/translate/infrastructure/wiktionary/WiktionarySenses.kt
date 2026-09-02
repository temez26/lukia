package io.lukia.features.translate.infrastructure.wiktionary

/**
 * The senses in a page of a Wiktionary, out of the text of the whole page.
 *
 * A page is every language that spells the word this way, each with its parts of
 * speech, its senses, and after them its inflections, its derived terms and its
 * references - and the query API hands all of it over as one run of text. What
 * has to come out of that is the first few things the word actually means.
 *
 * Three rules do it, and the second two were learned the hard way from a page
 * that put `support (monikko supports)` on the page in place of the sentence and
 * left `kannatus` as a footnote:
 *
 *  - A heading is scaffolding. They are asked for with their markup still on, so
 *    a heading is a line inside `=` signs and a sense is a line that is not.
 *  - A line that opens with the word being looked up is not a meaning of it. That
 *    is the headword and its inflections, and further down it is the derived
 *    terms - `support in decline` is a phrase the entry lists, not a sense.
 *  - Once senses have started, the next heading ends them. Otherwise a word
 *    spelled the same way in three languages hands back one sense from each, and
 *    the reader gets the Finnish for a word out of the French section.
 *
 * Deliberately still not an attempt to find the right language's section. Which
 * language the word belongs to is what the reader is looking it up to find out,
 * the headings naming it are written in the wiki's own language rather than in a
 * code this could match, and a wrong guess would throw away the only entry the
 * page had.
 *
 * [limit] is what separates the two things that ask this. A word being replaced
 * in the line it sits on can only carry a few senses before the paragraph stops
 * being readable; a reader who opened the entry on purpose asked for the entry.
 */
internal fun String.senses(word: String, limit: Int): List<String> {
    val senses = mutableListOf<String>()

    for (raw in lineSequence()) {
        val line = raw.trim()

        if (line.isEmpty()) continue

        if (line.startsWith("=")) {
            // The heading that opens the next thing, once there is something to
            // have finished.
            if (senses.isNotEmpty()) break

            continue
        }

        if (line.isScaffoldingFor(word)) continue

        if (line !in senses) senses += line
        if (senses.size == limit) break
    }

    // A word whose entry is nothing but its own headword - a loanword, a name -
    // would come back with nothing at all under the rules above. Its own line is
    // a poor answer and it is better than telling the reader there is no entry
    // when the page is right there.
    return senses.ifEmpty { fallbackSenses(limit) }
}

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

/** Whatever the page has, for an entry the rules above leave empty. */
private fun String.fallbackSenses(limit: Int): List<String> = lineSequence()
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .filterNot { it.startsWith("=") }
    .distinct()
    .take(limit)
    .toList()
