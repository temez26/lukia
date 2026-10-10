package io.lukia.core.infrastructure

import java.util.Locale

internal actual fun languageDisplayName(tag: String): String? {
    val locale = Locale.forLanguageTag(tag)
    val name = locale.getDisplayLanguage(Locale.getDefault())

    // Java answers with the tag itself for a subtag it does not know, which is
    // not a name and is not what this promises to return.
    return name.takeIf { it.isNotBlank() && !it.equals(locale.language, ignoreCase = true) }
}
