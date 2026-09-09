package io.lukia.core.infrastructure

/**
 * What the platform calls a language, in the language the device is set to.
 *
 * Asked rather than tabulated. The table it replaced held forty-one English
 * names, so a Finnish book read as "Finnish" on a Finnish device, anything
 * outside the forty-one had no name, and the library's filter disagreed with the
 * translation picker one screen away, which shows endonyms.
 *
 * Null where the platform does not know the tag, so the caller can fall back to
 * the tag itself rather than showing a name that is really a code.
 */
internal expect fun languageDisplayName(tag: String): String?
