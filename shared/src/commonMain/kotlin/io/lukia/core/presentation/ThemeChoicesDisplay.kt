package io.lukia.core.presentation

import androidx.compose.runtime.Composable
import io.lukia.core.domain.AppAccent
import io.lukia.core.domain.AppMotion
import io.lukia.core.domain.AppPalette
import io.lukia.core.domain.AppTheme
import io.lukia.resources.Res
import io.lukia.resources.accent_amber
import io.lukia.resources.accent_blue
import io.lukia.resources.accent_graphite
import io.lukia.resources.accent_green
import io.lukia.resources.accent_rose
import io.lukia.resources.accent_themed
import io.lukia.resources.accent_violet
import io.lukia.resources.motion_calm
import io.lukia.resources.motion_calm_detail
import io.lukia.resources.motion_playful
import io.lukia.resources.motion_playful_detail
import io.lukia.resources.motion_standard
import io.lukia.resources.motion_standard_detail
import io.lukia.resources.palette_crisp
import io.lukia.resources.palette_crisp_detail
import io.lukia.resources.palette_paper
import io.lukia.resources.palette_paper_detail
import io.lukia.resources.palette_slate
import io.lukia.resources.palette_slate_detail
import io.lukia.resources.theme_dark
import io.lukia.resources.theme_light
import io.lukia.resources.theme_system
import org.jetbrains.compose.resources.stringResource

/**
 * What the app's appearance settings are called, following the same pattern as
 * `AppErrorDisplay.kt`.
 *
 * Held apart from the enums themselves - see `ThemeChoices.kt` - because a
 * string resource is read from a composition, and a domain enum has none. Until
 * this file existed, [AppTheme] was shown by printing its constant's name, which
 * meant there was nothing to translate and that renaming a constant silently
 * changed the settings screen.
 */
@Composable
fun AppTheme.displayName(): String = when (this) {
    AppTheme.System -> stringResource(Res.string.theme_system)
    AppTheme.Light -> stringResource(Res.string.theme_light)
    AppTheme.Dark -> stringResource(Res.string.theme_dark)
}

@Composable
fun AppPalette.displayName(): String = when (this) {
    AppPalette.Crisp -> stringResource(Res.string.palette_crisp)
    AppPalette.Paper -> stringResource(Res.string.palette_paper)
    AppPalette.Slate -> stringResource(Res.string.palette_slate)
}

/** The second line under a palette, which is what actually distinguishes them. */
@Composable
fun AppPalette.displayDetail(): String = when (this) {
    AppPalette.Crisp -> stringResource(Res.string.palette_crisp_detail)
    AppPalette.Paper -> stringResource(Res.string.palette_paper_detail)
    AppPalette.Slate -> stringResource(Res.string.palette_slate_detail)
}

@Composable
fun AppAccent.displayName(): String = when (this) {
    AppAccent.Themed -> stringResource(Res.string.accent_themed)
    AppAccent.Blue -> stringResource(Res.string.accent_blue)
    AppAccent.Violet -> stringResource(Res.string.accent_violet)
    AppAccent.Green -> stringResource(Res.string.accent_green)
    AppAccent.Amber -> stringResource(Res.string.accent_amber)
    AppAccent.Rose -> stringResource(Res.string.accent_rose)
    AppAccent.Graphite -> stringResource(Res.string.accent_graphite)
}

@Composable
fun AppMotion.displayName(): String = when (this) {
    AppMotion.Calm -> stringResource(Res.string.motion_calm)
    AppMotion.Standard -> stringResource(Res.string.motion_standard)
    AppMotion.Playful -> stringResource(Res.string.motion_playful)
}

/** What each setting actually does, which the one word above cannot carry. */
@Composable
fun AppMotion.displayDetail(): String = when (this) {
    AppMotion.Calm -> stringResource(Res.string.motion_calm_detail)
    AppMotion.Standard -> stringResource(Res.string.motion_standard_detail)
    AppMotion.Playful -> stringResource(Res.string.motion_playful_detail)
}
