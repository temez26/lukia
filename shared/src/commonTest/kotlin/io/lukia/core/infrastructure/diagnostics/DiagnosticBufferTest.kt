package io.lukia.core.infrastructure.diagnostics

import io.lukia.core.domain.diagnostics.DiagnosticArea
import io.lukia.core.domain.diagnostics.DiagnosticEntry
import io.lukia.core.domain.diagnostics.DiagnosticLevel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class DiagnosticBufferTest {

    @Test
    fun entriesComeBackOldestFirst() {
        val buffer = DiagnosticBuffer()

        buffer.add(entry("first"))
        buffer.add(entry("second"))

        assertEquals(listOf("first", "second"), buffer.entries.value.map { it.event })
    }

    /**
     * The cap is what stops one pathological document from taking the record of
     * everything that led up to it, so what it drops has to be the oldest.
     */
    @Test
    fun theOldestIsDroppedOnceTheBufferIsFull() {
        val buffer = DiagnosticBuffer(capacity = 3)

        repeat(5) { buffer.add(entry("event-$it")) }

        assertEquals(
            listOf("event-2", "event-3", "event-4"),
            buffer.entries.value.map { it.event },
        )
    }

    @Test
    fun clearingLeavesNothingBehind() {
        val buffer = DiagnosticBuffer()

        buffer.add(entry("first"))
        buffer.clear()

        assertEquals(emptyList(), buffer.entries.value)
    }

    /**
     * Entries arrive from wherever the failure happened - several PDF readers
     * are somewhere in the same book at once - so an append that lost one to a
     * race would lose exactly the entry a maintainer was looking for.
     */
    @Test
    fun nothingIsLostWhenSeveralThingsRecordAtOnce() = runTest {
        val buffer = DiagnosticBuffer(capacity = 500)

        (0 until 10).map { writer ->
            async { repeat(20) { buffer.add(entry("writer-$writer")) } }
        }.awaitAll()

        assertEquals(200, buffer.entries.value.size)
    }

    private fun entry(event: String) = DiagnosticEntry(
        at = Instant.fromEpochMilliseconds(0),
        level = DiagnosticLevel.Warning,
        area = DiagnosticArea.Reading,
        event = event,
        detail = null,
        causeType = null,
    )
}
