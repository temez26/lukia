package io.lukia.shared.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import io.lukia.core.domain.AppAccent
import io.lukia.core.domain.AppPalette

/**
 * The palette and accent chosen in Settings, for a part of the app that wears
 * its own lightness rather than the window's.
 *
 * The reader is that part: its Light and Dark themes are the reader's choice,
 * and its chrome is still this palette and this accent at that lightness, so
 * leaving the shelf for a book does not change what the app looks like.
 */
@Immutable
data class ThemeChoice(
    val palette: AppPalette,
    val accent: AppAccent,
) {
    fun colorScheme(darkTheme: Boolean): ColorScheme = colorSchemeFor(palette, darkTheme, accent)
}

val LocalThemeChoice = staticCompositionLocalOf { ThemeChoice(AppPalette.Crisp, AppAccent.Themed) }
