package io.lukia.features.translate.domain.settings

/**
 * The language a reader wants sentences rendered in.
 *
 * A closed list rather than free text: the setting is a picker, and a provider
 * asked to translate into a typo produces something confidently wrong rather
 * than an error. [tag] is what a translation service is told; [endonym] is what
 * the picker shows, because a list of languages reads best in the languages it
 * names.
 *
 * Short on purpose. This is the set worth having on day one, not a claim about
 * which languages matter - adding one is a line here and nothing else.
 */
enum class TargetLanguage(
    /** A BCP 47 tag - `fi`, `pt-BR`. */
    val tag: String,
    /** The language's own name for itself, for the picker. */
    val endonym: String,
    /** Its English name, for the prompt - which is written in English. */
    val englishName: String,
) {
    English("en", "English", "English"),
    Finnish("fi", "Suomi", "Finnish"),
    Swedish("sv", "Svenska", "Swedish"),
    German("de", "Deutsch", "German"),
    French("fr", "Français", "French"),
    Spanish("es", "Español", "Spanish"),
    Portuguese("pt", "Português", "Portuguese"),
    Italian("it", "Italiano", "Italian"),
    Dutch("nl", "Nederlands", "Dutch"),
    Polish("pl", "Polski", "Polish"),
    Turkish("tr", "Türkçe", "Turkish"),
    Russian("ru", "Русский", "Russian"),
    Arabic("ar", "العربية", "Arabic"),
    Hindi("hi", "हिन्दी", "Hindi"),
    ChineseSimplified("zh-Hans", "简体中文", "Simplified Chinese"),
    Japanese("ja", "日本語", "Japanese"),
    Korean("ko", "한국어", "Korean"),
    ;

    companion object {

        /**
         * The default, and the one a reader is most likely to already know well
         * enough to read a book beside. Nothing about it is a recommendation -
         * it is the setting somebody changes once.
         */
        val Default = English
    }
}
