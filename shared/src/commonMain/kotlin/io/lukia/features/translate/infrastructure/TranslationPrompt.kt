package io.lukia.features.translate.infrastructure

import io.lukia.features.translate.domain.TranslationContext
import io.lukia.features.translate.domain.TranslationRequest

/**
 * What a language model is told, in the two parts every provider splits it into.
 *
 * [system] is the same for every request in a language and [user] carries the
 * book - which is also the order they cache in, so a reader working through one
 * chapter re-sends the standing instructions for free.
 */
data class TranslationPrompt(
    val system: String,
    val user: String
)

/**
 * How much of the surrounding text is worth sending.
 *
 * Enough to hold the paragraph a sentence sits in, which is where the pronouns
 * and the subject of the ambiguous word actually are. Past that it stops paying:
 * a whole chapter costs latency on every tap and settles nothing the paragraph
 * did not.
 */
private const val MAX_SURROUNDING_CHARS = 1200

/**
 * Turns a request into the words a model is given.
 *
 * A pure function, and separate from any provider on purpose: the prompt is the
 * part of this feature most worth being able to read, argue about and test, and
 * none of that should mean standing up an HTTP client. What it deliberately does
 * *not* say is what shape to answer in - that is each provider's business, and
 * the one talking to Claude constrains the shape with a schema rather than by
 * asking nicely.
 */
fun buildTranslationPrompt(request: TranslationRequest): TranslationPrompt {
    val language = request.targetLanguage.englishName

    val system = """
        You translate single passages out of books for someone who is reading
        that book in a language they are still learning. They have the original
        in front of them; what they want from you is the same passage in
        $language, close enough that they can go straight back to reading.

        Keep the register the original set: an archaic line stays archaic,
        dialogue stays spoken, a joke stays a joke. Translate idioms by what
        they mean rather than word by word. Leave names as the book writes them,
        unless $language has its own established form of the name.

        The details of the book and the text around the passage are there to
        settle which sense of a word is meant. Use them, and translate the
        passage itself and nothing else.

        Where a word stays genuinely ambiguous once you have used that context,
        pick the reading the book supports and say which in one short clause.
        Where it does not, say nothing - a remark on every sentence is noise
        rather than help.
    """.trimIndent().collapseWrapping()

    return TranslationPrompt(system = system, user = request.userMessage())
}

private fun TranslationRequest.userMessage(): String = buildString {
    val book = context.describe(passage = text)

    if (book.isNotEmpty()) {
        appendLine(book)
        appendLine()
    }

    appendLine("Translate this passage into ${targetLanguage.englishName}:")
    append(text.trim())
}

/**
 * The book as labelled lines, leaving out everything the document did not say.
 *
 * A field the book never declared is left out rather than sent as "Unknown": a
 * plain text file has no title, no author and no genre, and telling a model
 * three times that it knows nothing is worse than telling it once by omission.
 */
private fun TranslationContext.describe(passage: String): String = buildString {
    bookTitle?.let { appendLabelled("Book", it) }

    if (authors.isNotEmpty()) {
        appendLabelled(if (authors.size == 1) "Author" else "Authors", authors.joinToString(", "))
    }

    if (genres.isNotEmpty()) {
        appendLabelled(if (genres.size == 1) "Genre" else "Genres", genres.joinToString(", "))
    }

    sourceLanguage?.let { appendLabelled("Written in", it) }
    chapterTitle?.let { appendLabelled("Chapter", it) }

    surroundingText
        // A one-sentence paragraph is its own surroundings. Sending it twice
        // costs tokens to tell the model something it was already told.
        ?.takeIf { it.trim() != passage.trim() }
        ?.windowAround(passage, MAX_SURROUNDING_CHARS)
        ?.let {
            if (isNotEmpty()) appendLine()
            appendLine("The passage appears in this text:")
            append(it)
        }
}.trimEnd()

private fun StringBuilder.appendLabelled(label: String, value: String) {
    appendLine("$label: ${value.trim().collapseWrapping()}")
}

/**
 * A window of this text holding [passage], at most [maxChars] long.
 *
 * Centred on the passage rather than taken from the front, because a sentence
 * near the end of a long paragraph would otherwise arrive with the context of a
 * different part of the page - and the words on either side of it are the whole
 * reason for sending any of this.
 */
private fun String.windowAround(passage: String, maxChars: Int): String? {
    val text = trim()
    if (text.isEmpty()) return null
    if (text.length <= maxChars) return text

    val found = text.indexOf(passage.trim())
    val margin = ((maxChars - passage.length) / 2).coerceAtLeast(0)

    val start = if (found < 0) 0 else (found - margin).coerceAtLeast(0)
    val end = (start + maxChars).coerceAtMost(text.length)
    // Pulled back off the end so a passage near the close of a long paragraph
    // still gets the full window rather than whatever fits after it.
    val from = (end - maxChars).coerceAtLeast(0)

    return buildString {
        if (from > 0) append("…")
        append(text.substring(from, end))
        if (end < text.length) append("…")
    }
}

/**
 * Undoes the line breaks this file's source indentation puts in, so the prompt
 * reads as paragraphs rather than as a column of short lines. Blank lines are
 * kept: they are what separates one instruction from the next.
 */
private fun String.collapseWrapping(): String =
    split("\n\n").joinToString("\n\n") { paragraph ->
        paragraph.split("\n").joinToString(" ") { it.trim() }.trim()
    }
