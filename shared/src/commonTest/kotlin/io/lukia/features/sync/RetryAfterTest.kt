package io.lukia.features.sync

import io.lukia.features.sync.application.retryAfter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class RetryAfterTest {

    @Test
    fun theWaitStartsAtHalfAMinuteAndDoubles() {
        assertEquals(listOf(30.seconds, 1.minutes, 2.minutes, 4.minutes), (1..4).map(::retryAfter))
    }

    @Test
    fun theWaitNeverPassesAQuarterOfAnHour() {
        assertEquals(15.minutes, retryAfter(6))
        assertEquals(15.minutes, retryAfter(1_000))
    }
}
