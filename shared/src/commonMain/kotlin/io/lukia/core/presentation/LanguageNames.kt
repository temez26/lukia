package io.lukia.core.presentation

import io.lukia.core.infrastructure.languageDisplayName

/**
 * A language tag as a language.
 *
 * Only the primary subtag is looked at, so `en-GB` and `en-US` both read as
 * English - the region is a distinction for a spell checker rather than for
 * someone deciding whether they can read a book. A tag the platform does not
 * know is shown as it was written, upper-cased, which is still more use than
 * hiding it.
 *
 * The name comes from the device rather than from a table in this app - see
 * [languageDisplayName]. The table it replaced held forty-one languages in
 * English, which meant a Finnish reader was told their Finnish book was in
 * "Finnish" and a book in Aymara was told nothing at all.
 *
 * Shared rather than kept beside the screen that first needed it: a book's
 * details name its language and the library filters by it, and two tables would
 * be two chances to disagree about what `nb` is called.
 */
fun languageName(tag: String?): String? {
    val cleaned = tag?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val primary = cleaned.substringBefore('-').substringBefore('_').lowercase()

    return languageDisplayName(primary) ?: cleaned.uppercase()
}
