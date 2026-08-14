package io.lukia.core.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AppErrorTest {

    @Test
    fun aFailureCarriesItsErrorBackOutUnchanged() {
        val result = AppError.UnsupportedFileType.asFailure()

        assertTrue(result.isFailure)
        assertEquals(AppError.UnsupportedFileType, result.exceptionOrNull()?.toAppError())
    }

    /** The point of the catch-all: nothing escapes without a shape the UI knows. */
    @Test
    fun anUnknownThrowableBecomesUnexpectedCarryingItsMessage() {
        val error = IllegalStateException("disk on fire").toAppError()

        assertEquals(AppError.Unexpected("disk on fire"), error)
    }

    @Test
    fun aThrowableWithoutAMessageStillNarrows() {
        assertEquals(AppError.Unexpected(null), IllegalStateException().toAppError())
    }

    @Test
    fun runCatchingAppReturnsTheValueOnSuccess() {
        assertEquals(Result.success(7), runCatchingApp { 7 })
    }

    @Test
    fun runCatchingAppCapturesAFailure() {
        val result = runCatchingApp { error("nope") }

        assertEquals(AppError.Unexpected("nope"), result.exceptionOrNull()?.toAppError())
    }

    /**
     * Cancellation is how a scope shuts down, not a failure to report. Catching
     * it here would break structured concurrency, so it has to travel on.
     */
    @Test
    fun runCatchingAppRethrowsCancellation() = runTest {
        assertFailsWith<CancellationException> {
            runCatchingApp { throw CancellationException("scope closed") }
        }
    }
}
