package io.lukia.features.reader.domain.text

/**
 * One sentence, and where it sits in the text it was cut from.
 *
 * [start] and [end] index the original string - end exclusive - which is what
 * lets the reader highlight the sentence someone tapped without searching for
 * it again. [index] is its position in the passage, and is what a [TextAnchor]
 * remembers: unlike an offset it survives the same book being re-set at a
 * different type size.
 */
data class Sentence(
    val index: Int,
    val start: Int,
    val end: Int,
    val text: String,
)

/**
 * Cuts a passage into the sentences a reader would point at.
 *
 * Sentences, not words or paragraphs, because that is the unit a translation is
 * actually meaningful at: a word out of its clause is a dictionary entry rather
 * than a reading, and a whole paragraph replaced at once loses the place the
 * reader had in it.
 *
 * Deliberately a heuristic rather than a model of punctuation: it gets decimals,
 * initials and common abbreviations right, and a split one clause early costs
 * the reader a second tap - far cheaper than not offering sentences at all.
 * Whitespace between sentences belongs to neither, so a highlight sits on the
 * words rather than trailing into the gap.
 */
fun splitIntoSentences(text: String): List<Sentence> {
    val sentences = mutableListOf<Sentence>()
    var sentenceStart = 0
    var cursor = 0

    while (cursor < text.length) {
        if (text[cursor] !in TERMINATORS) {
            cursor++
            continue
        }

        // "..." and "?!" end one sentence between them, not three.
        var end = cursor
        while (end < text.length && text[end] in TERMINATORS) end++

        // A closing quote or bracket belongs to the sentence it closes: the
        // full stop inside `"Run!" he said` is not the end of anything.
        while (end < text.length && text[end] in CLOSERS) end++

        val endsHere = text.breaksAfter(end) &&
            !text.isAbbreviatedAt(cursor) &&
            text.hasWordsBetween(sentenceStart, end)

        if (endsHere) {
            sentences.addSentence(text, sentenceStart, end)
            sentenceStart = end
        }

        cursor = end
    }

    // Whatever is left after the last full stop, including a passage that has
    // no punctuation at all - a heading, or a line of verse.
    sentences.addSentence(text, sentenceStart, text.length)

    return sentences
}

/** The sentence covering [offset], or null if the offset falls outside them all. */
fun List<Sentence>.sentenceAt(offset: Int): Sentence? =
    firstOrNull { offset >= it.start && offset < it.end }

private val TERMINATORS = charArrayOf('.', '!', '?', '…', '。', '！', '？')

private val CLOSERS = charArrayOf('"', '\'', '»', '”', '’', ')', ']', '」', '』')

/**
 * Short on purpose. Every entry here is a word that is followed by a full stop
 * and then more of the same sentence; anything less clear-cut than that costs a
 * real sentence break to catch a rare one - `no.` is an abbreviation far less
 * often than it is an answer.
 */
private val ABBREVIATIONS = setOf(
    "mr", "mrs", "ms", "mx", "dr", "prof", "rev", "hon", "st", "jr", "sr",
    "vs", "etc", "ie", "eg", "cf", "al", "fig", "vol", "ch", "pp", "approx",
)

/**
 * Whether a sentence can end at [end] - which is to say, whether what follows
 * reads as the start of a new one.
 *
 * The lowercase test keeps dialogue in one piece. `"Run!" he shouted` has a full
 * ending inside the quotation marks and carries straight on, and every novel is
 * full of it - a reader who tapped that line would otherwise get two words back
 * and have to tap again for who shouted them.
 */
private fun String.breaksAfter(end: Int): Boolean {
    if (end >= length) return true
    if (!this[end].isWhitespace()) return false

    var next = end
    while (next < length && this[next].isWhitespace()) next++

    return next >= length || !this[next].isLowerCase()
}

/** True when the full stop at [terminator] is part of a word rather than an end. */
private fun String.isAbbreviatedAt(terminator: Int): Boolean {
    if (this[terminator] != '.') return false

    var wordStart = terminator
    while (wordStart > 0 && this[wordStart - 1].isLetter()) wordStart--

    val word = substring(wordStart, terminator)

    // A lone letter before a full stop is an initial: "J. R. R. Tolkien" is one
    // name, not three sentences.
    if (word.length == 1) return true

    return word.lowercase() in ABBREVIATIONS
}

/**
 * Whether there is anything to read between these offsets. Guards against a
 * passage that opens on punctuation - a line of dots, or the closing quote of
 * the sentence before it - being called a sentence of its own.
 */
private fun String.hasWordsBetween(from: Int, to: Int): Boolean {
    for (index in from until to) {
        if (this[index].isLetterOrDigit()) return true
    }

    return false
}

private fun MutableList<Sentence>.addSentence(text: String, from: Int, to: Int) {
    var start = from
    var end = to

    while (start < end && text[start].isWhitespace()) start++
    while (end > start && text[end - 1].isWhitespace()) end--

    if (start >= end || !text.hasWordsBetween(start, end)) return

    add(Sentence(index = size, start = start, end = end, text = text.substring(start, end)))
}
