package io.lukia.features.translate.domain.lookup

/**
 * Somewhere on the web that can be opened about one word.
 *
 * The counterpart to [io.lukia.features.translate.domain.text.WordLookup], and
 * deliberately not part of it: that setting chooses who answers *inside* the
 * book, in a line over the word, and everything in it has to be something this
 * app can ask and read back. This one chooses where the reader is sent when a
 * line is not enough - a dictionary site with its inflections and its example
 * sentences, a translator with its alternatives - and what is there is somebody
 * else's page, opened in a browser and read as its authors laid it out.
 *
 * Which site is not a thing worth having opinions about, so there are none here
 * beyond a few that are known to work. A site is a name and an address with the
 * word written into it, and [Custom] is the whole point of that shape: anything
 * with a searchable URL is one line of settings away from being on this list.
 *
 * [urlTemplate] carries three placeholders, all optional:
 *
 *  - `{word}` the word, percent-encoded
 *  - `{to}` the language the reader is reading into
 *  - `{from}` the language the book declared, or [anyLanguage] where it declared
 *    none
 */
enum class WordSite(
    val urlTemplate: String,
    /**
     * What this site calls the languages, keyed by the plain language subtag of
     * a BCP 47 tag - `en`, `fi`, `pt`.
     *
     * Empty for a site that calls them what everybody else does, which is most
     * of them. A site with its own names for languages cannot simply be handed a
     * tag, and being handed one anyway is how a dictionary ends up searching the
     * wrong pair or none at all.
     */
    val languageCodes: Map<String, String> = emptyMap(),
    /**
     * What stands for "whichever language" here - for a book that declared none,
     * and for a language this site does not have.
     *
     * A real value rather than nothing, because these placeholders sit in the
     * middle of somebody else's query string: leaving one empty asks the site a
     * question with a hole in it, while asking it about every language it has is
     * a question it can answer.
     */
    val anyLanguage: String = "auto",
) {

    GoogleTranslate(
        urlTemplate = "https://translate.google.com/?sl={from}&tl={to}&text={word}&op=translate",
    ),

    Wiktionary(
        urlTemplate = "https://{to}.wiktionary.org/wiki/{word}",
    ),

    Sanakirja(
        urlTemplate = "https://www.sanakirja.org/search.php?q={word}&l={from}&l2={to}",
        // Numbered rather than named, and the numbers are the site's own: they
        // are the values of the two language menus on its search page and there
        // is nothing to derive them from. The four languages of the picker it
        // has no entry for - Arabic, Hindi, Chinese, Korean - fall through to
        // [anyLanguage] and get a search across every language it does have,
        // which finds the word rather than refusing to look.
        languageCodes = mapOf(
            "bg" to "1",
            "et" to "2",
            "en" to "3",
            "es" to "4",
            "eo" to "5",
            "it" to "6",
            "el" to "7",
            "la" to "8",
            "lv" to "9",
            "lt" to "10",
            "no" to "11",
            "pt" to "12",
            "pl" to "13",
            "fr" to "14",
            "sv" to "15",
            "de" to "16",
            "fi" to "17",
            "da" to "18",
            "cs" to "19",
            "tr" to "20",
            "hu" to "21",
            "ru" to "22",
            "nl" to "23",
            "ja" to "24",
        ),
        // The dash at the top of both of the site's language menus.
        anyLanguage = "-1",
    ),

    /**
     * Anywhere else. The address is the reader's, kept in the settings beside
     * this choice rather than here - see
     * [io.lukia.features.translate.domain.settings.TranslationSettings.wordSiteUrlTemplate].
     */
    Custom(
        urlTemplate = "",
    ),
    ;

    companion object {

        /**
         * Google, because it is the one of these that answers for every language
         * in the picker. The others are better at what they cover and cover less.
         */
        val Default = GoogleTranslate
    }
}
