package io.lukia.core.infrastructure

import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** A count, grouped the way the reader's own locale groups one. */
class NumberFormatsTest {

    private val wasDefault: Locale = Locale.getDefault()

    @AfterTest
    fun restoreLocale() = Locale.setDefault(wasDefault)

    @Test
    fun smallCountsAreLeftAlone() {
        Locale.setDefault(Locale.ENGLISH)

        assertEquals("0", formatCount(0))
        assertEquals("348", formatCount(348))
    }

    /**
     * The figure this is actually for. A reader two years in has five figures of
     * pages behind them, and "12480" is a number somebody has to count digits on.
     */
    @Test
    fun largeCountsAreGrouped() {
        Locale.setDefault(Locale.ENGLISH)

        assertEquals("12,480", formatCount(12_480))
    }

    /** And the separator is not a comma everywhere. */
    @Test
    fun theGroupingSeparatorFollowsTheLocale() {
        Locale.setDefault(Locale.GERMAN)

        assertEquals("12.480", formatCount(12_480))
    }
}
