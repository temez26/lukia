package io.lukia.core.infrastructure

import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.localizedStringForLanguageCode

internal actual fun languageDisplayName(tag: String): String? =
    NSLocale.currentLocale.localizedStringForLanguageCode(tag)?.takeIf { it.isNotBlank() }
