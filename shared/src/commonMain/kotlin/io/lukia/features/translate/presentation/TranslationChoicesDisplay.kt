package io.lukia.features.translate.presentation

import androidx.compose.runtime.Composable
import io.lukia.features.translate.domain.lookup.WordSite
import io.lukia.features.translate.domain.provider.TranslationProvider
import io.lukia.features.translate.domain.settings.WordLookup
import io.lukia.resources.Res
import io.lukia.resources.provider_anthropic
import io.lukia.resources.provider_local
import io.lukia.resources.provider_openai
import io.lukia.resources.word_lookup_dictionary
import io.lukia.resources.word_lookup_dictionary_footnote
import io.lukia.resources.word_lookup_google
import io.lukia.resources.word_lookup_google_footnote
import io.lukia.resources.word_site_custom
import io.lukia.resources.word_site_custom_footnote
import io.lukia.resources.word_site_google
import io.lukia.resources.word_site_google_footnote
import io.lukia.resources.word_site_sanakirja
import io.lukia.resources.word_site_sanakirja_footnote
import io.lukia.resources.word_site_wiktionary
import io.lukia.resources.word_site_wiktionary_footnote
import org.jetbrains.compose.resources.stringResource

/**
 * What every translation choice is called, and what is worth saying under it.
 *
 * These used to be fields on the enums, which put four paragraphs of settings
 * copy inside `domain` - where a string resource cannot follow, reading one
 * taking a composition, and where pointing at the UI is the one direction
 * `ArchitectureTest` exists to prevent.
 *
 * What a passage unit is called lives in `core.presentation` beside the type
 * instead: a tap on a sentence is a question about the page whether or not
 * anything translates it.
 */

/**
 * Two of these name a company rather than a thing, and are resources so the
 * picker reads from one place - not because they want translating.
 */
@Composable
fun TranslationProvider.displayName(): String = when (this) {
    TranslationProvider.Anthropic -> stringResource(Res.string.provider_anthropic)
    TranslationProvider.OpenAi -> stringResource(Res.string.provider_openai)
    TranslationProvider.Local -> stringResource(Res.string.provider_local)
}

@Composable
fun WordLookup.displayName(): String = when (this) {
    WordLookup.Google -> stringResource(Res.string.word_lookup_google)
    WordLookup.Wiktionary -> stringResource(Res.string.word_lookup_dictionary)
}

/** What choosing this one actually gets the reader, which the name cannot say. */
@Composable
fun WordLookup.displayFootnote(): String = when (this) {
    WordLookup.Google -> stringResource(Res.string.word_lookup_google_footnote)
    WordLookup.Wiktionary -> stringResource(Res.string.word_lookup_dictionary_footnote)
}

@Composable
fun WordSite.displayName(): String = when (this) {
    WordSite.GoogleTranslate -> stringResource(Res.string.word_site_google)
    WordSite.Wiktionary -> stringResource(Res.string.word_site_wiktionary)
    WordSite.Sanakirja -> stringResource(Res.string.word_site_sanakirja)
    WordSite.Custom -> stringResource(Res.string.word_site_custom)
}

@Composable
fun WordSite.displayFootnote(): String = when (this) {
    WordSite.GoogleTranslate -> stringResource(Res.string.word_site_google_footnote)
    WordSite.Wiktionary -> stringResource(Res.string.word_site_wiktionary_footnote)
    WordSite.Sanakirja -> stringResource(Res.string.word_site_sanakirja_footnote)
    WordSite.Custom -> stringResource(Res.string.word_site_custom_footnote)
}
