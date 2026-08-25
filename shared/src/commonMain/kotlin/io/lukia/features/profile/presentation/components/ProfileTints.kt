package io.lukia.features.profile.presentation.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.lukia.features.profile.domain.ProfileTint
import io.lukia.shared.presentation.theme.Accents

/** The disc's fill and what is legible on it, which only ever travel together. */
internal data class TintColors(val fill: Color, val content: Color)

/**
 * What a chosen tint is painted with.
 *
 * [ProfileTint.Accent] defers to the app's own highlight, so a reader who has
 * chosen a palette in Settings gets a profile that belongs to it - and gets the
 * matching content colour with it, which is the only way that pairing stays
 * legible across the three palettes and both themes.
 *
 * The six named hues take their light-theme values whichever theme is on, unlike
 * the accents in Settings. Those are chosen to carry a word against a surface
 * and so go paler at night; this is a solid disc with white on top of it, and a
 * pale fill under white is a disc nobody can read. A deep hue is legible against
 * a light background and a dark one alike, which is what a disc has to be.
 */
@Composable
internal fun tintColors(tint: ProfileTint): TintColors = when (tint) {
    ProfileTint.Accent -> TintColors(
        fill = MaterialTheme.colorScheme.primary,
        content = MaterialTheme.colorScheme.onPrimary,
    )

    ProfileTint.Blue -> onWhite(Accents.Blue)
    ProfileTint.Violet -> onWhite(Accents.Violet)
    ProfileTint.Green -> onWhite(Accents.Green)
    ProfileTint.Amber -> onWhite(Accents.Amber)
    ProfileTint.Rose -> onWhite(Accents.Rose)
    ProfileTint.Graphite -> onWhite(Accents.Graphite)
}

private fun onWhite(fill: Color) = TintColors(fill = fill, content = Color.White)
