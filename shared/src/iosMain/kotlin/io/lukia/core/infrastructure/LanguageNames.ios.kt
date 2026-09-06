package io.lukia.core.infrastructure

import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.localizedStringForLanguageCode

/**
 * Cut down to the language subtag before it is looked up.
 *
 * `localizedStringForLanguageCode` wants a language and is given a tag: handed
 * "pt-BR" or "zh-Hant" it answers nothing at all, where Android and the desktop
 * both name the language and ignore the rest - `Locale.getDisplayLanguage` is
 * about the language and nothing else. A book whose metadata carries a region is
 * ordinary, so without this the same shelf reads "Portuguese" on one platform
 * and "pt-BR" on another.
 *
 * Both separators are cut because both turn up in the wild: BCP 47 writes a tag
 * with hyphens and a POSIX locale writes it with an underscore, and an EPUB's
 * language element has been seen carrying either.
 */
internal actual fun languageDisplayName(tag: String): String? {
    val language = tag.substringBefore('-').substringBefore('_')

    if (language.isBlank()) return null

    return NSLocale.currentLocale.localizedStringForLanguageCode(language)
        ?.takeIf { it.isNotBlank() }
}
