package io.lukia.features.reader.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderSettingsTest {

    @Test
    fun theDefaultsAreTheComfortableConfiguration() {
        val settings = ReaderSettings()

        assertEquals(ReaderTheme.Light, settings.theme)
        assertEquals(ReadingWidth.Wide, settings.matchingWidth)
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

    /** A pinch can ask for anything; the page has to stay a readable size. */
    @Test
    fun aPageScaleFromOutsideTheRangeIsClamped() {
        assertEquals(
            ReaderSettings.MAX_PAGE_SCALE,
            ReaderSettings().withPageScale(40f).pageScale
        )
        assertEquals(
            ReaderSettings.MIN_PAGE_SCALE,
            ReaderSettings().withPageScale(0.01f).pageScale
        )
    }

    /**
     * A pinch usually settles between two presets, and the panel says so by
     * marking none of them rather than the nearest one.
     */
    @Test
    fun aScaleBetweenPresetsMatchesNoneOfThem() {
        assertNull(ReaderSettings().withPageScale(1.13f).matchingWidth)
        assertEquals(
            ReadingWidth.Larger,
            ReaderSettings().withPageScale(ReadingWidth.Larger.fraction).matchingWidth
        )
    }

    /** The steps have to actually get wider, in the order they are offered. */
    @Test
    fun pageWidthsAreOrderedFromNarrowestToWidest() {
        val fractions = ReadingWidth.entries.map { it.fraction }

        assertEquals(fractions.sorted(), fractions)
        assertEquals(fractions.distinct(), fractions)
    }

    /** [ReadingWidth.Wide] is the page fitting the column exactly. */
    @Test
    fun oneStepFitsTheColumnExactly() {
        assertEquals(1f, ReadingWidth.Wide.fraction)
        assertFalse(ReadingWidth.Wide.overflowsColumn)
    }

    /**
     * Only the steps past fitting overflow, and there has to be at least one -
     * they are what makes zooming into fixed-size type possible at all.
     */
    @Test
    fun theStepsPastFittingOverflowTheColumn() {
        val overflowing = ReadingWidth.entries.filter { it.overflowsColumn }

        assertTrue(overflowing.isNotEmpty())
        assertTrue(overflowing.all { it.fraction > ReadingWidth.Wide.fraction })
        assertTrue(ReadingWidth.entries.none { it.fraction < 1f && it.overflowsColumn })
    }

    @Test
    fun lineSpacingsAreOrderedFromTightToRelaxed() {
        assertTrue(LineSpacing.Tight.multiplier < LineSpacing.Normal.multiplier)
        assertTrue(LineSpacing.Normal.multiplier < LineSpacing.Relaxed.multiplier)
    }
}
