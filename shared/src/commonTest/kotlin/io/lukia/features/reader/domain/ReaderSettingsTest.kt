package io.lukia.features.reader.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderSettingsTest {

    @Test
    fun theDefaultsAreTheComfortableConfiguration() {
        val settings = ReaderSettings()

        assertEquals(ReaderTheme.Light, settings.theme)
        assertEquals(ReadingWidth.Wide, settings.readingWidth)
        assertEquals(100, settings.textScalePercent)
    }

    @Test
    fun textGrowsAndShrinksByOneStep() {
        val settings = ReaderSettings()

        assertEquals(110, settings.withLargerText().textScalePercent)
        assertEquals(90, settings.withSmallerText().textScalePercent)
    }

    @Test
    fun textStopsAtTheLargestSize() {
        var settings = ReaderSettings()
        repeat(50) { settings = settings.withLargerText() }

        assertEquals(ReaderSettings.MAX_TEXT_SCALE, settings.textScale)
        assertFalse(settings.canEnlargeText)
    }

    @Test
    fun textStopsAtTheSmallestSize() {
        var settings = ReaderSettings()
        repeat(50) { settings = settings.withSmallerText() }

        assertEquals(ReaderSettings.MIN_TEXT_SCALE, settings.textScale)
        assertFalse(settings.canReduceText)
    }

    /**
     * Stepping by a fraction accumulates error, and the drifted value is what
     * gets persisted - so a size stepped up and back down has to land exactly
     * where it started.
     */
    @Test
    fun steppingUpAndBackReturnsTheOriginalSize() {
        var settings = ReaderSettings()
        repeat(6) { settings = settings.withLargerText() }
        repeat(6) { settings = settings.withSmallerText() }

        assertEquals(ReaderSettings.DEFAULT_TEXT_SCALE, settings.textScale)
        assertEquals(100, settings.textScalePercent)
    }

    @Test
    fun aSizeFromOutsideTheRangeIsClamped() {
        assertEquals(ReaderSettings.MAX_TEXT_SCALE, ReaderSettings().withTextScale(9f).textScale)
        assertEquals(ReaderSettings.MIN_TEXT_SCALE, ReaderSettings().withTextScale(0.1f).textScale)
    }

    @Test
    fun bothEndsOfTheRangeAreReachable() {
        val settings = ReaderSettings()

        assertTrue(settings.canEnlargeText)
        assertTrue(settings.canReduceText)
    }

    /** Narrower settings have to actually be narrower, in order. */
    @Test
    fun readingWidthsAreOrderedFromNarrowToWide() {
        assertTrue(ReadingWidth.Narrow.fraction < ReadingWidth.Medium.fraction)
        assertTrue(ReadingWidth.Medium.fraction < ReadingWidth.Wide.fraction)
        assertEquals(1f, ReadingWidth.Wide.fraction)
    }

    @Test
    fun lineSpacingsAreOrderedFromTightToRelaxed() {
        assertTrue(LineSpacing.Tight.multiplier < LineSpacing.Normal.multiplier)
        assertTrue(LineSpacing.Normal.multiplier < LineSpacing.Relaxed.multiplier)
    }
}
