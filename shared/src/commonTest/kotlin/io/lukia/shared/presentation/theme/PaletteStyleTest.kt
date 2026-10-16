package io.lukia.shared.presentation.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import io.lukia.core.domain.AppPalette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PaletteStyleTest {

    @Test
    fun crispIsTheAppsOwnShapesAndType() {
        assertEquals(LukiaShapes, shapesFor(AppPalette.Crisp))
        assertEquals(LukiaTypography, typographyFor(AppPalette.Crisp, Serif))
    }

    @Test
    fun everyPaletteHasCornersOfItsOwn() {
        val shapes = AppPalette.entries.map(::shapesFor)

        assertEquals(shapes.size, shapes.toSet().size, "two palettes share their corners")
    }

    @Test
    fun iconButtonsAreRoundOnlyWhereThePaletteIsSoft() {
        assertEquals(CircleShape, iconButtonShapeFor(AppPalette.Crisp))
        assertEquals(CircleShape, iconButtonShapeFor(AppPalette.Paper))
        assertNotEquals(CircleShape, iconButtonShapeFor(AppPalette.Slate))
        assertNotEquals(CircleShape, iconButtonShapeFor(AppPalette.Ledger))
    }

    /** Paper sets its headings in the serif at regular weight, Ledger in bold. */
    @Test
    fun paperAndLedgerSetHeadingsInTheSerif() {
        val paper = typographyFor(AppPalette.Paper, Serif)
        val ledger = typographyFor(AppPalette.Ledger, Serif)

        assertEquals(Serif, paper.displaySmall.fontFamily)
        assertEquals(FontWeight.Normal, paper.titleMedium.fontWeight)
        assertEquals(Serif, ledger.titleMedium.fontFamily)
        assertEquals(FontWeight.Bold, ledger.titleMedium.fontWeight)
    }

    @Test
    fun slateSetsHeavierTighterHeadingsInTheSans() {
        val slate = typographyFor(AppPalette.Slate, Serif)
        val crisp = LukiaTypography

        assertNull(slate.displaySmall.fontFamily)
        assertTrue(slate.displaySmall.fontWeight!!.weight > crisp.displaySmall.fontWeight!!.weight)
        assertTrue(slate.titleMedium.letterSpacing.value < crisp.titleMedium.letterSpacing.value)
    }

    /** What is read in quantity stays in the app's sans whatever the palette. */
    @Test
    fun bodyTextAndLabelsAreTheSameInEveryPalette() {
        AppPalette.entries.forEach { palette ->
            val type = typographyFor(palette, Serif)

            assertEquals(LukiaTypography.bodyLarge, type.bodyLarge, "$palette changed body text")
            assertEquals(LukiaTypography.labelSmall, type.labelSmall, "$palette changed labels")
        }
    }
}

/** Stands in for the bundled serif, which only a composition can load. */
private val Serif = FontFamily.Serif
