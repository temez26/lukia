package io.lukia.features.settings.domain

import io.lukia.core.domain.AppAccent
import io.lukia.core.domain.AppMotion
import io.lukia.core.domain.AppPalette
import io.lukia.core.domain.AppTheme

/**
 * Settings that apply to the whole app, as opposed to the reader's own
 * presentation settings, which belong to a book being read and live in
 * [io.lukia.features.reader.domain.ReaderSettings].
 *
 * One value carried whole, so adding a setting later is a field here and a key in
 * the repository rather than a new pipe through the app.
 *
 * The four choices it is made of belong to the design system rather than to this
 * screen: the theme is what realises them, and every screen wears the result
 * whether or not it knows a settings screen exists. This is the value that
 * records which of them somebody picked.
 */
data class AppSettings(
    val theme: AppTheme = AppTheme.System,
    val palette: AppPalette = AppPalette.Crisp,
    val accent: AppAccent = AppAccent.Themed,
    val motion: AppMotion = AppMotion.Standard,
)
