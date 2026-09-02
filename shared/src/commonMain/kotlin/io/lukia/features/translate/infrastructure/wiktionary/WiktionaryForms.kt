package io.lukia.features.translate.infrastructure.wiktionary

/**
 * How short a word may be and still be followed to.
 *
 * Two, because `go`, `be` and `do` are the base forms most often arrived at from
 * an inflection - `goes`, `been`, `did` - and a floor of three would miss every
 * one of them.
 */
private const val MIN_BASE_LENGTH = 2

/**
 * The word this entry only points at, or null when it defines something itself.
 *
 * A wiki's page for an inflected form is often not a definition at all. Looking
 * up `values` on the Finnish Wiktionary answers `monikkomuoto sanasta value` -
 * "plural of value" - which is a fact about grammar and no help whatsoever to
 * somebody who wanted to know it means `arvo`. Without this the reader is told
 * what their word is a form of and left to look that up themselves, which on a
 * language that inflects everything is most of the words in the book.
 *
 * What makes a line a pointer is the small word in front of the last one. Every
 * language writes these the same way round - `plural of value`,
 * `monikkomuoto sanasta value`, `böjningsform av värde` - and putting the weight
 * on that word is what lets an irregular form be followed at all: `juoksi` and
 * `juosta` share four letters and `went` and `go` share none, so nothing about
 * the two words themselves could have said they belong together.
 *
 * Three things are accepted as a pointer, in descending order of how sure they
 * are, because following the wrong word is worse than not following:
 *
 *  - A word that only ever introduces a base form. `sanasta` and `verbistä` are
 *    not something a definition says.
 *  - A word the looked-up one begins with. That is what makes `values` follow to
 *    `value` and `running` to `run` whatever the line around them says.
 *  - A plain preposition, but only in a line that is describing grammar. `of` is
 *    the whole of what English has to mark these with, and on its own it would
 *    read `a piece of land` as a pointer at `land`.
 *
 * Followed from the first sense only. A page that opens with a meaning is
 * defining the word, whatever it lists after that.
 */
internal fun List<String>.baseForm(word: String): String? =
    firstOrNull()?.pointsAtBaseOf(word)

private fun String.pointsAtBaseOf(word: String): String? {
    val tokens = split(' ', '\t', '\n')
        .map { it.trim { character -> !character.isLetterOrDigit() } }
        .filter { it.isNotEmpty() }

    if (tokens.size < 2) return null

    val candidate = tokens.last()

    if (!candidate.isFollowable(word)) return null

    return candidate.takeIf { tokens.pointAtIt(word, candidate) }
}

private fun String.isFollowable(word: String): Boolean =
    length >= MIN_BASE_LENGTH && first().isLetter() && !equals(word, ignoreCase = true)

private fun List<String>.pointAtIt(word: String, candidate: String): Boolean {
    val particle = this[size - 2].lowercase()

    return particle in NamedPointers ||
        word.startsWith(candidate, ignoreCase = true) ||
        (particle in Prepositions && any { it.lowercase() in FormWords })
}

/** Words that only stand between a form and the word it belongs to. */
private val NamedPointers = setOf(
    "sanasta",
    "sanan",
    "verbistä",
    "verbista",
    "muodosta",
    "böjningsform",
)

/** What the same phrase is held together with where the language has no better word. */
private val Prepositions = setOf("of", "av", "von", "van")

/**
 * What a line is saying when it describes a form rather than a meaning.
 *
 * Only ever consulted to decide whether a bare preposition is a pointer, so it
 * needs to cover the way English names an inflection and nothing more - the
 * languages that mark the phrase properly are already past this.
 */
private val FormWords = setOf(
    "plural",
    "singular",
    "participle",
    "indicative",
    "subjunctive",
    "imperative",
    "conditional",
    "potential",
    "infinitive",
    "gerund",
    "comparative",
    "superlative",
    "tense",
    "form",
    "forms",
    "inflection",
    "inflected",
    "conjugation",
    "declension",
    "past",
    "present",
    "nominative",
    "genitive",
    "accusative",
    "partitive",
    "inessive",
    "elative",
    "illative",
    "adessive",
    "ablative",
    "allative",
    "essive",
    "translative",
    "abessive",
    "comitative",
    "instructive",
    "spelling",
    "böjningsform",
)
