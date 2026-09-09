package io.lukia.features.translate.infrastructure

/**
 * How much a model is allowed to write for one passage.
 *
 * Two things fit inside this and only one is the translation: every model worth
 * running thinks before it answers, and on both wire formats that thinking is
 * charged to the same allowance as the reply.
 *
 * The thinking is also the half that cannot be predicted from the passage. It is
 * never shown and arrives on its own field, so from outside there is no
 * difference between a model that spent its allowance reasoning and one still
 * writing: the stream ends saying "length", with nothing to read.
 *
 * That is what a flat 1024 did to a paragraph. Measured against a local model:
 *
 *     one sentence,  1024 allowed  ->  stopped on its own after 31 tokens
 *     one paragraph, 1024 allowed  ->  cut off: 3,000 characters of thinking,
 *                                      then the answer half written
 *     one paragraph, 4096 allowed  ->  stopped on its own after 497 tokens
 *
 * A sentence never noticed the ceiling and a paragraph could not get under it,
 * which is what the reader saw: sentences translated, paragraphs reporting
 * themselves as too long, while the same model with no ceiling set translated
 * them perfectly well.
 *
 * So the allowance follows the passage, over a floor with room to think about a
 * short one. It costs nothing to set high: it is a ceiling, and a model that
 * says everything in fifty tokens is charged for fifty.
 */
internal fun tokenAllowanceFor(passage: String): Int =
    (passage.length / CHARS_PER_TOKEN * ANSWER_HEADROOM).coerceIn(MIN_TOKENS, MAX_TOKENS)

/**
 * What a character of the answer is worth in tokens, pessimistically.
 *
 * The reader is translating *into* their own language, and the languages that
 * tokenize worst are the ones this is most used for: a tokenizer trained mostly
 * on English splits Finnish or Hungarian into pieces of two or three characters
 * where it would have taken an English word whole. Guessing four characters a
 * token - the English figure - would put the ceiling under the answer for
 * exactly the readers who need it most.
 */
private const val CHARS_PER_TOKEN = 2

/**
 * How many times the passage's own length to allow.
 *
 * One for the translation, which comes out near the length it went in at, and
 * the rest for the thinking in front of it and the JSON around it.
 */
private const val ANSWER_HEADROOM = 4

/**
 * Enough for a model to think about a short passage and still answer. A
 * sentence needs a few dozen tokens of translation; all the rest of this is
 * headroom for a model that decides to reason about an idiom.
 */
private const val MIN_TOKENS = 4096

/**
 * What no single passage is allowed past, however long it is.
 *
 * A limit on a model that has stopped translating and started rambling - which
 * is the only thing this now catches, and the only thing a limit was ever good
 * for.
 */
private const val MAX_TOKENS = 8192
