package io.lukia.shared.presentation.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontFamily
import io.lukia.core.domain.AppPalette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class PaletteStyleTest {

    @Test
    fun onlyLedgerChangesShapesAndType() {
        AppPalette.entries.filter { it != AppPalette.Ledger }.forEach { palette ->
            assertEquals(LukiaShapes, shapesFor(palette), "$palette changed the shapes")
            assertEquals(LukiaTypography, typographyFor(palette), "$palette changed the type")
        }

        assertNotEquals(LukiaShapes, shapesFor(AppPalette.Ledger))
    }

    @Test
    fun iconButtonsAreRoundExceptInLedger() {
        AppPalette.entries.filter { it != AppPalette.Ledger }.forEach { palette ->
            assertEquals(CircleShape, iconButtonShapeFor(palette), "$palette squared its icon buttons")
        }

        assertNotEquals(CircleShape, iconButtonShapeFor(AppPalette.Ledger))
    }

    /** Headings go serif; what is read in quantity stays in the app's sans. */
    @Test
    fun ledgerSetsHeadingsInSerifAndLeavesBodyTextAlone() {
        val ledger = typographyFor(AppPalette.Ledger)

        assertEquals(FontFamily.Serif, ledger.displaySmall.fontFamily)
        assertEquals(FontFamily.Serif, ledger.titleMedium.fontFamily)
        assertNull(ledger.bodyLarge.fontFamily)
        assertNull(ledger.labelSmall.fontFamily)
    }
}
