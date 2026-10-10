package io.lukia.core.infrastructure

import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.localizedStringForLanguageCode

/**
 * Cut down to the language subtag before it is looked up.
 *
 * `localizedStringForLanguageCode` wants a language and is given a tag: handed
 * "pt-BR" or "zh-Hant" it answers nothing, where Android and the desktop name
 * the language and ignore the rest. Metadata carrying a region is ordinary, so
 * without this a shelf reads "Portuguese" on one platform and "pt-BR" on another.
 *
 * Both separators are cut because both turn up: BCP 47 uses hyphens, a POSIX
 * locale an underscore, and an EPUB's language element has carried either.
 */
internal actual fun languageDisplayName(tag: String): String? {
    val language = tag.substringBefore('-').substringBefore('_')

    if (language.isBlank()) return null

    return NSLocale.currentLocale.localizedStringForLanguageCode(language)
        ?.takeIf { it.isNotBlank() }
}
