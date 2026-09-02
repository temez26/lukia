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
    fun anUnknownThrowableBecomesUnexpected() {
        assertEquals(AppError.Unexpected(), IllegalStateException().toAppError())
    }

    /**
     * The message belongs to whoever wrote the code that threw, and
     * `AppErrorDisplay` would put it in front of a reader - as a file path with
     * their own name in it, on the desktop. Dropped here so that only prose
     * written for a reader can reach the field it is shown from.
     */
    @Test
    fun anUnknownThrowableLeavesItsOwnMessageBehind() {
        val error = IllegalStateException("disk on fire").toAppError()

        assertEquals(AppError.Unexpected(), error)
    }

    @Test
    fun runCatchingAppReturnsTheValueOnSuccess() {
        assertEquals(Result.success(7), runCatchingApp { 7 })
    }

    @Test
    fun runCatchingAppCapturesAFailure() {
        val result = runCatchingApp { error("nope") }

        assertEquals(AppError.Unexpected(), result.exceptionOrNull()?.toAppError())
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
