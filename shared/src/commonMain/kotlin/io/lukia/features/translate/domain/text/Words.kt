package io.lukia.features.translate.domain.text

/**
 * One word of a sentence, and where it sits in the text that sentence was cut
 * from.
 *
 * [start] and [end] index the whole passage rather than the sentence, exactly as
 * [Sentence] does, so a word can be highlighted and replaced by the same code
 * that handles a sentence without anything having to remember which of the two
 * it is looking at.
 *
 * [sentenceIndex] travels with it because a word alone is not addressable: the
 * fourth word of a paragraph means nothing once a sentence before it has been
 * translated and is a different length, while the fourth word of the second
 * sentence is still the fourth word of the second sentence.
 */
data class Word(
    val sentenceIndex: Int,
    val index: Int,
    val start: Int,
    val end: Int,
    val text: String,
)

/**
 * The words of this sentence, in the order they are read.
 *
 * A word here is what somebody would point at with one finger, which is not
 * quite what a space-separated split gives: the punctuation around a word is not
 * part of it, but the apostrophe and the hyphen inside one are - "don't" and
 * "self-evident" are each one thing to look up, and offering "don" and "t" as
 * two words to translate would be worse than offering nothing.
 */
fun Sentence.words(): List<Word> {
    val words = mutableListOf<Word>()
    var cursor = 0

    while (cursor < text.length) {
        if (!text[cursor].startsWord()) {
            cursor++
            continue
        }

        var end = cursor + 1

        while (end < text.length && text.continuesWordAt(end)) end++

        words += Word(
            sentenceIndex = index,
            index = words.size,
            start = start + cursor,
            end = start + end,
            text = text.substring(cursor, end),
        )

        cursor = end
    }

    return words
}

/** Every word of every sentence, in reading order. */
fun List<Sentence>.words(): List<Word> = flatMap { it.words() }

/** The word covering [offset], or null where the offset falls between words. */
fun List<Word>.wordAt(offset: Int): Word? =
    firstOrNull { offset >= it.start && offset < it.end }

private fun Char.startsWord(): Boolean = isLetterOrDigit()

/**
 * Whether the character at [index] carries on the word before it.
 *
 * A joiner only counts when a letter follows it, so the apostrophe in "don't"
 * is inside the word and the one closing 'quoted' is not - and a dash between
 * two clauses does not weld them into a single unpointable word.
 */
private fun String.continuesWordAt(index: Int): Boolean {
    val character = this[index]

    if (character.isLetterOrDigit()) return true
    if (character !in WORD_JOINERS) return false

    return getOrNull(index + 1)?.isLetterOrDigit() == true
}

private val WORD_JOINERS = charArrayOf('\'', '’', '-', '‐', '.')
