package io.lukia.shared.presentation.theme

import io.lukia.core.domain.AppAccent
import io.lukia.core.domain.AppPalette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * What a palette and an accent add up to.
 *
 * The colours themselves are a matter of taste and are not asserted on; what is
 * asserted is that the two settings do what they say - one changes the surfaces,
 * the other changes the highlight, and neither reaches into the other's half.
 */
class ColorSchemeTest {

    @Test
    fun aChosenAccentIsWhatTheAppHighlightsWith() {
        val scheme = colorSchemeFor(AppPalette.Crisp, darkTheme = false, accent = AppAccent.Green)

        assertEquals(Accents.Green, scheme.primary)
        // Everything Material reaches for to mean "this one" has to agree, or an
        // accent would reach the progress bar and stop at the buttons.
        assertEquals(Accents.Green, scheme.secondary)
        assertEquals(Accents.Green, scheme.onPrimaryContainer)
    }

    @Test
    fun eachPaletteBringsItsOwnHighlightWhenNoneIsChosen() {
        val crisp = colorSchemeFor(AppPalette.Crisp, darkTheme = false, accent = AppAccent.Themed)
        val slate =
            colorSchemeFor(AppPalette.Slate, darkTheme = false, accent = AppAccent.Themed)

        assertEquals(Accents.Blue, crisp.primary)
        assertEquals(Accents.Amber, slate.primary)
    }

    @Test
    fun choosingAnAccentLeavesTheSurfacesAlone() {
        val themed =
            colorSchemeFor(AppPalette.Paper, darkTheme = false, accent = AppAccent.Themed)
        val rose = colorSchemeFor(AppPalette.Paper, darkTheme = false, accent = AppAccent.Rose)

        assertEquals(themed.background, rose.background)
        assertEquals(themed.surface, rose.surface)
        assertEquals(themed.onSurface, rose.onSurface)
        assertNotEquals(themed.primary, rose.primary)
    }

    @Test
    fun choosingAPaletteChangesWhatTheAppIsMadeOf() {
        val crisp = colorSchemeFor(AppPalette.Crisp, darkTheme = false, accent = AppAccent.Blue)
        val paper = colorSchemeFor(AppPalette.Paper, darkTheme = false, accent = AppAccent.Blue)

        assertNotEquals(crisp.background, paper.background)
        assertNotEquals(crisp.surface, paper.surface)
        // ...but the accent they were both given is untouched by that.
        assertEquals(crisp.primary, paper.primary)
    }

    /**
     * Every palette has to have somewhere to put a card. A dark scheme whose
     * surface matched its background would leave menus and sheets floating with
     * no edge to them.
     */
    @Test
    fun everyPaletteSeparatesItsCardsFromItsBackground() {
        AppPalette.entries.forEach { palette ->
            listOf(false, true).forEach { darkTheme ->
                val scheme = colorSchemeFor(palette, darkTheme, AppAccent.Themed)

                assertNotEquals(
                    scheme.background,
                    scheme.surface,
                    "$palette ${if (darkTheme) "dark" else "light"} has nowhere to lay a card.",
                )
                assertTrue(
                    scheme.onSurface != scheme.surface,
                    "$palette ${if (darkTheme) "dark" else "light"} writes in its own surface.",
                )
            }
        }
    }
}
