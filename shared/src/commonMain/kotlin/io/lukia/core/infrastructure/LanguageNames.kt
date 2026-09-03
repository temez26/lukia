package io.lukia.core.infrastructure

/**
 * What the platform calls a language, in the language the device is set to.
 *
 * Asked rather than tabulated. This replaced a table of forty-one English names
 * kept in the app, which meant a Finnish book read as "Finnish" on a Finnish
 * device, that a language outside the forty-one had no name at all, and that the
 * library's own filter disagreed with the translation picker one screen away -
 * which shows a language's endonym and always did.
 *
 * Null where the platform does not know the tag, so the caller can fall back to
 * the tag itself rather than showing a name that is really a code.
 */
internal expect fun languageDisplayName(tag: String): String?
