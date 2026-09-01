package io.lukia.features.reader.presentation.pagination

import kotlin.test.Test
import kotlin.test.assertEquals

class MeasuringThreadsTest {

    /**
     * The desktop draws with Skia too, and shares the one unguarded cache of
     * styles that makes measuring a book on several threads unsafe - see
     * [maxParallelMeasurers].
     */
    @Test
    fun a_book_is_measured_on_one_thread() {
        assertEquals(1, paginationWorkers())
    }
}
