package io.lukia.core.presentation

/**
 * A language tag as a language.
 *
 * Only the primary subtag is looked at, so `en-GB` and `en-US` both read as
 * English - the region is a distinction for a spell checker rather than for
 * someone deciding whether they can read a book. A tag not in this list is shown
 * as it was written, upper-cased, which is still more use than hiding it.
 *
 * Shared rather than kept beside the screen that first needed it: a book's
 * details name its language and the library filters by it, and two tables would
 * be two chances to disagree about what `nb` is called.
 */
fun languageName(tag: String?): String? {
    val cleaned = tag?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val primary = cleaned.substringBefore('-').substringBefore('_').lowercase()

    return LanguageNames[primary] ?: cleaned.uppercase()
}

/**
 * The languages a reading app actually meets, by ISO 639-1 code.
 *
 * Deliberately not the full set of some two hundred: the rest fall back to their
 * tag, which is what a book in Aymara would have shown anyway had it been listed
 * and mistranslated.
 */
private val LanguageNames = mapOf(
    "ar" to "Arabic",
    "bg" to "Bulgarian",
    "ca" to "Catalan",
    "cs" to "Czech",
    "da" to "Danish",
    "de" to "German",
    "el" to "Greek",
    "en" to "English",
    "es" to "Spanish",
    "et" to "Estonian",
    "fa" to "Persian",
    "fi" to "Finnish",
    "fr" to "French",
    "he" to "Hebrew",
    "hi" to "Hindi",
    "hr" to "Croatian",
    "hu" to "Hungarian",
    "id" to "Indonesian",
    "is" to "Icelandic",
    "it" to "Italian",
    "ja" to "Japanese",
    "ko" to "Korean",
    "lt" to "Lithuanian",
    "lv" to "Latvian",
    "nb" to "Norwegian",
    "nl" to "Dutch",
    "nn" to "Norwegian",
    "no" to "Norwegian",
    "pl" to "Polish",
    "pt" to "Portuguese",
    "ro" to "Romanian",
    "ru" to "Russian",
    "sk" to "Slovak",
    "sl" to "Slovenian",
    "sr" to "Serbian",
    "sv" to "Swedish",
    "th" to "Thai",
    "tr" to "Turkish",
    "uk" to "Ukrainian",
    "vi" to "Vietnamese",
    "zh" to "Chinese",
)
