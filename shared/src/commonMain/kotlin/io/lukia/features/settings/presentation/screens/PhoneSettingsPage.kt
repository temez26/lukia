package io.lukia.features.settings.presentation.screens

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * What a group of settings needs to know when it is a page of its own on a phone
 * rather than one of five on a window's page - see [SettingsAreaScreen].
 *
 * A local rather than a parameter on every group, because what changes is how a
 * dozen sections lay themselves out, not what they hold: a phone shows each
 * section's explanation under it instead of behind a mark, drops a heading the
 * page's own title already says, and trades the longest controls for rows that
 * open them.
 */
internal class PhoneSettingsPage(
    val title: String,
    /** Opens the list of languages to translate into, as a page of its own. */
    val onPickLanguage: () -> Unit,
)

/** Null in a window, where `SettingsBody` lays the groups out as they always were. */
internal val LocalPhoneSettingsPage = staticCompositionLocalOf<PhoneSettingsPage?> { null }
