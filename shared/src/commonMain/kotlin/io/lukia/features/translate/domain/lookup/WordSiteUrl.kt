package io.lukia.features.translate.domain.lookup

import io.lukia.features.translate.domain.settings.TargetLanguage

/**
 * The address to open for one word, or null when there is nothing to open.
 *
 * Null rather than a best effort, because the two ways this fails are both ways
 * the reader would be sent nowhere useful: a template they have not filled in
 * yet, and one that does not name the word at all. Opening a browser on a search
 * page with no search in it is worse than the button doing nothing.
 *
 * A site that names none of the languages simply does not get them - most
 * dictionaries are a single pair and settle it themselves - so the placeholders
 * are replaced where they appear rather than required.
 */
fun WordSite.urlFor(
    template: String,
    word: String,
    targetLanguage: TargetLanguage,
    sourceLanguage: String? = null,
): String? {
    val term = word.trim()

    if (term.isEmpty() || !template.contains(WORD_PLACEHOLDER)) return null

    return template
        .replace(WORD_PLACEHOLDER, term.percentEncoded())
        .replace("{to}", languageCode(targetLanguage.tag))
        .replace("{from}", languageCode(sourceLanguage))
}

/**
 * What this site calls one language, or what it calls "whichever language" when
 * the tag is missing or is one the site has no entry for.
 *
 * A site with no names of its own is handed the tag, which is what every site
 * that takes one expects. See [WordSite.languageCodes].
 */
private fun WordSite.languageCode(tag: String?): String {
    val short = tag?.shortTag()?.takeIf { it.isNotEmpty() } ?: return anyLanguage

    if (languageCodes.isEmpty()) return short

    return languageCodes[short] ?: anyLanguage
}

private const val WORD_PLACEHOLDER = "{word}"

/**
 * The language on its own, without the script or the region a document may have
 * written after it.
 *
 * Every site here takes the short form - `zh` rather than `zh-Hans`, and there is
 * no Wiktionary at `pt-BR.wiktionary.org` - and a tag with a subtag on it is the
 * ordinary case in an EPUB rather than the exception. It is also what a site's
 * own names for languages are keyed by, so this happens before the lookup rather
 * than instead of it.
 */
private fun String.shortTag(): String = trim().substringBefore('-').lowercase()

/**
 * The word, written so it survives being put into an address.
 *
 * Encoded to the unreserved set of RFC 3986, which is the one encoding that is
 * correct in both halves of a URL: these sites want the word in a query for some
 * of them and in the path for others, and a space escaped as `+` is a plus sign
 * once it lands in a path.
 */
private fun String.percentEncoded(): String = buildString {
    encodeToByteArray().forEach { byte ->
        val char = byte.toInt().toChar()

        if (char.isUnreserved()) append(char) else append(byte.percentEscape())
    }
}

private fun Char.isUnreserved(): Boolean =
    this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9' || this in "-._~"

private const val BYTE_MASK = 0xFF
private const val HEX = 16

private fun Byte.percentEscape(): String =
    "%" + (toInt() and BYTE_MASK).toString(HEX).uppercase().padStart(2, '0')
